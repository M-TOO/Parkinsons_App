"""Run from the repo root:  SECRET_KEY=... uvicorn backend.main:app --host 0.0.0.0 --port 8000"""
import os
import uuid
from datetime import datetime, timedelta, timezone

import bcrypt
import jwt
from fastapi import Depends, FastAPI, File, HTTPException, UploadFile
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from .database import engine, get_db
from .models import AssessmentSession, Base, User
from .schemas import MotorIn, SessionOut, TokenOut, TypingIn, UserCreate, UserLogin

SECRET_KEY = os.environ.get("SECRET_KEY", "pGVPSKEiHr4b3hUaEZDFegzwRQlGftBX-_wC4xAbg-6vWHL2qM2RTNTpTQhK_xUH")

app = FastAPI(title="Parkinson's Assessment API")
Base.metadata.create_all(engine)
bearer = HTTPBearer()


# ── Auth helpers ───────────────────────────────────────────────────────────
def make_token(user: User) -> TokenOut:
    exp = datetime.now(timezone.utc) + timedelta(days=7)
    token = jwt.encode({"sub": str(user.id), "exp": exp}, SECRET_KEY, algorithm="HS256")
    return TokenOut(access_token=token, user=user)


def current_user(creds: HTTPAuthorizationCredentials = Depends(bearer),
                 db: Session = Depends(get_db)) -> User:
    try:
        user_id = int(jwt.decode(creds.credentials, SECRET_KEY, algorithms=["HS256"])["sub"])
    except (jwt.PyJWTError, KeyError, ValueError):
        raise HTTPException(401, "Invalid or expired token")
    user = db.get(User, user_id)
    if user is None:
        raise HTTPException(401, "Invalid or expired token")
    return user


# ── Register / Login ───────────────────────────────────────────────────────
@app.post("/register", response_model=TokenOut, status_code=201)
def register(body: UserCreate, db: Session = Depends(get_db)):
    email = body.email.lower()
    if db.query(User).filter_by(email=email).first():
        raise HTTPException(409, "Email already registered")
    hashed = bcrypt.hashpw(body.password.encode(), bcrypt.gensalt()).decode()
    user = User(full_name=body.full_name, email=email,
                password_hash=hashed, year_of_birth=body.year_of_birth)
    db.add(user)
    db.commit()
    db.refresh(user)
    return make_token(user)


@app.post("/login", response_model=TokenOut)
def login(body: UserLogin, db: Session = Depends(get_db)):
    user = db.query(User).filter_by(email=body.email.lower()).first()
    if user is None or not bcrypt.checkpw(body.password.encode(), user.password_hash.encode()):
        raise HTTPException(401, "Incorrect email or password")
    return make_token(user)


# ── Score helpers ──────────────────────────────────────────────────────────
def band(p, low, mid, high):
    return None if p is None else low if p < 0.4 else high if p > 0.6 else mid


def summarise(s: AssessmentSession) -> None:
    drawing = [x for x in (s.spiral_pd_score, s.wave_pd_score) if x is not None]
    d = sum(drawing) / len(drawing) if drawing else None
    t = s.typing_pd_score
    s.tapping_status = band(t, "Typical pattern", "Inconclusive", "Atypical pattern")
    s.tapping_stability_match = None if t is None else round((1 - t) * 100, 1)
    s.drawing_status = band(d, "Typical pattern", "Inconclusive", "Atypical pattern")
    s.drawing_tremor_level = band(d, "Low", "Moderate", "High")
    known = [x for x in (t, d) if x is not None]
    if known:
        m = sum(known) / len(known)
        s.overall_stability_score = round((1 - m) * 100, 1)
        s.risk_label = band(m, "No flags", "Inconclusive", "Review suggested")


def own_session(db: Session, session_id: str, user: User) -> AssessmentSession:
    try:
        uuid.UUID(session_id)
    except ValueError:
        raise HTTPException(422, "session_id must be a UUID")
    s = db.get(AssessmentSession, session_id)
    if s is None:
        s = AssessmentSession(session_id=session_id, user_id=user.id)
        db.add(s)
    elif s.user_id != user.id:
        raise HTTPException(404, "Session not found")
    return s


def save(db: Session, s: AssessmentSession) -> AssessmentSession:
    db.commit()
    db.refresh(s)
    return s


# ── Patient Session endpoints ──────────────────────────────────────────────
@app.post("/sessions/{session_id}/typing", response_model=SessionOut)
def submit_typing(session_id: str, body: TypingIn,
                  db: Session = Depends(get_db), user: User = Depends(current_user)):
    s = own_session(db, session_id, user)
    s.typing_pd_score = 0.3
    summarise(s)
    return save(db, s)


@app.post("/sessions/{session_id}/drawing/{task}", response_model=SessionOut)
def submit_drawing(session_id: str, task: str, image: UploadFile = File(...),
                   db: Session = Depends(get_db), user: User = Depends(current_user)):
    if task not in ("spiral", "wave"):
        raise HTTPException(404, "task must be 'spiral' or 'wave'")
    data = image.file.read(10_000_001)
    if not data or len(data) > 10_000_000:
        raise HTTPException(400, "Empty or oversized image")
    s = own_session(db, session_id, user)
    setattr(s, f"{task}_pd_score", 0.25)
    summarise(s)
    return save(db, s)


@app.patch("/sessions/{session_id}/motor", response_model=SessionOut)
def submit_motor(session_id: str, body: MotorIn,
                 db: Session = Depends(get_db), user: User = Depends(current_user)):
    s = own_session(db, session_id, user)
    for field, value in body.model_dump(exclude_none=True).items():
        setattr(s, field, value)
    return save(db, s)


@app.get("/sessions", response_model=list[SessionOut])
def history(db: Session = Depends(get_db), user: User = Depends(current_user)):
    return (db.query(AssessmentSession)
            .filter_by(user_id=user.id)
            .order_by(AssessmentSession.created_at.desc())
            .limit(100).all())


# ── Admin & Clinician endpoints (Colleague 2 / M-TOO) ──────────────────────
@app.get("/admin/patients")
def list_patients(db: Session = Depends(get_db), admin: User = Depends(current_user)):
    users = db.query(User).all()
    return [{
        "id": u.id,
        "full_name": u.full_name,
        "email": u.email,
        "year_of_birth": u.year_of_birth,
        "role": u.role,
        "total_assessments": len(u.sessions)
    } for u in users]


@app.get("/admin/flagged")
def list_flagged_sessions(db: Session = Depends(get_db), admin: User = Depends(current_user)):
    flagged = db.query(AssessmentSession).filter(
        AssessmentSession.risk_label.in_(["Review suggested", "Atypical pattern", "ATYPICAL"])
    ).all()
    return flagged
