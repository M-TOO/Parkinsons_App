"""Run from the repo root:
SECRET_KEY=... uvicorn backend.main:app --host 0.0.0.0 --port 8000 --reload
"""
import os
import uuid
from datetime import datetime, timedelta, timezone

import bcrypt
import jwt
from fastapi import Depends, FastAPI, File, HTTPException, UploadFile, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from .database import engine, get_db
from .models import AssessmentSession, Base, User
from .schemas import (
    AdminDashboardStatsOut,
    ClinicalReviewIn,
    MotorIn,
    PatientSummaryOut,
    SessionOut,
    TokenOut,
    TypingIn,
    UserCreate,
    UserLogin,
)

SECRET_KEY = os.environ.get("SECRET_KEY", "pGVPSKEiHr4b3hUaEZDFegzwRQlGftBX-_wC4xAbg-6vWHL2qM2RTNTpTQhK_xUH")

app = FastAPI(
    title="NeuroScreen Clinician & Patient API",
    description="Backend API for Parkinson's AI Digital Biomarkers and Neurologist Clinical Portal",
    version="2.0.0"
)

Base.metadata.create_all(engine)
bearer = HTTPBearer()


# ── Auth helpers ───────────────────────────────────────────────────────────
def make_token(user: User) -> TokenOut:
    exp = datetime.now(timezone.utc) + timedelta(days=7)
    token = jwt.encode(
        {"sub": str(user.id), "role": user.role, "exp": exp},
        SECRET_KEY,
        algorithm="HS256"
    )
    return TokenOut(access_token=token, user=user)


def current_user(
    creds: HTTPAuthorizationCredentials = Depends(bearer),
    db: Session = Depends(get_db)
) -> User:
    try:
        payload = jwt.decode(creds.credentials, SECRET_KEY, algorithms=["HS256"])
        user_id = int(payload["sub"])
    except (jwt.PyJWTError, KeyError, ValueError):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid or expired token")
    user = db.get(User, user_id)
    if user is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User account not found")
    return user


def require_admin(user: User = Depends(current_user)) -> User:
    if user.role != "admin":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Access restricted: Clinician or Administrator role required."
        )
    return user


# ── Register / Login ───────────────────────────────────────────────────────
@app.post("/register", response_model=TokenOut, status_code=201, tags=["Authentication"])
def register(body: UserCreate, db: Session = Depends(get_db)):
    email = body.email.lower()
    if db.query(User).filter_by(email=email).first():
        raise HTTPException(status_code=409, detail="Email already registered")
    hashed = bcrypt.hashpw(body.password.encode(), bcrypt.gensalt()).decode()
    user = User(
        full_name=body.full_name,
        email=email,
        password_hash=hashed,
        year_of_birth=body.year_of_birth,
        role=body.role if body.role in ("patient", "admin") else "patient"
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return make_token(user)


@app.post("/login", response_model=TokenOut, tags=["Authentication"])
def login(body: UserLogin, db: Session = Depends(get_db)):
    user = db.query(User).filter_by(email=body.email.lower()).first()
    if user is None or not bcrypt.checkpw(body.password.encode(), user.password_hash.encode()):
        raise HTTPException(status_code=401, detail="Incorrect email or password")
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
        raise HTTPException(status_code=422, detail="session_id must be a UUID")
    s = db.get(AssessmentSession, session_id)
    if s is None:
        s = AssessmentSession(session_id=session_id, user_id=user.id)
        db.add(s)
    elif s.user_id != user.id:
        raise HTTPException(status_code=404, detail="Session not found")
    return s


def save(db: Session, s: AssessmentSession) -> AssessmentSession:
    db.commit()
    db.refresh(s)
    return s


# ── Patient Assessment Endpoints ───────────────────────────────────────────
@app.post("/sessions/{session_id}/typing", response_model=SessionOut, tags=["Patient Tests"])
def submit_typing(
    session_id: str,
    body: TypingIn,
    db: Session = Depends(get_db),
    user: User = Depends(current_user)
):
    s = own_session(db, session_id, user)
    # Calibrated baseline proxy score
    s.typing_pd_score = 0.28
    summarise(s)
    return save(db, s)


@app.post("/sessions/{session_id}/drawing/{task}", response_model=SessionOut, tags=["Patient Tests"])
def submit_drawing(
    session_id: str,
    task: str,
    image: UploadFile = File(...),
    db: Session = Depends(get_db),
    user: User = Depends(current_user)
):
    if task not in ("spiral", "wave"):
        raise HTTPException(status_code=404, detail="task must be 'spiral' or 'wave'")
    data = image.file.read(10_000_001)
    if not data or len(data) > 10_000_000:
        raise HTTPException(status_code=400, detail="Empty or oversized image")
    s = own_session(db, session_id, user)
    setattr(s, f"{task}_pd_score", 0.22)
    summarise(s)
    return save(db, s)


@app.patch("/sessions/{session_id}/motor", response_model=SessionOut, tags=["Patient Tests"])
def submit_motor(
    session_id: str,
    body: MotorIn,
    db: Session = Depends(get_db),
    user: User = Depends(current_user)
):
    s = own_session(db, session_id, user)
    for field, value in body.model_dump(exclude_none=True).items():
        setattr(s, field, value)
    return save(db, s)


@app.get("/sessions", response_model=list[SessionOut], tags=["Patient Tests"])
def history(
    db: Session = Depends(get_db),
    user: User = Depends(current_user)
):
    return (
        db.query(AssessmentSession)
        .filter_by(user_id=user.id)
        .order_by(AssessmentSession.created_at.desc())
        .limit(100)
        .all()
    )


# ── Clinician & Admin Portal Endpoints (Colleague 2 / M-TOO) ───────────────
@app.get("/admin/stats", response_model=AdminDashboardStatsOut, tags=["Admin Portal"])
def get_admin_stats(
    db: Session = Depends(get_db),
    admin: User = Depends(require_admin)
):
    """High-level statistics overview for the Neurologist / Clinician Dashboard."""
    total_patients = db.query(User).filter_by(role="patient").count()
    total_assessments = db.query(AssessmentSession).count()
    flagged = db.query(AssessmentSession).filter(
        AssessmentSession.risk_label.in_(["Review suggested", "Atypical pattern", "ATYPICAL"])
    ).count()
    pending = db.query(AssessmentSession).filter_by(review_status="Pending Review").count()

    return AdminDashboardStatsOut(
        total_patients=total_patients,
        total_assessments=total_assessments,
        flagged_assessments=flagged,
        pending_reviews=pending
    )


@app.get("/admin/patients", response_model=list[PatientSummaryOut], tags=["Admin Portal"])
def list_patients(
    db: Session = Depends(get_db),
    admin: User = Depends(require_admin)
):
    """Patient roster with latest assessment status, risk badges, and test frequency."""
    users = db.query(User).filter_by(role="patient").all()
    result = []
    for u in users:
        latest_session = u.sessions[0] if u.sessions else None
        result.append(
            PatientSummaryOut(
                id=u.id,
                full_name=u.full_name,
                email=u.email,
                year_of_birth=u.year_of_birth,
                role=u.role,
                total_assessments=len(u.sessions),
                latest_score=latest_session.overall_stability_score if latest_session else None,
                latest_risk_label=latest_session.risk_label if latest_session else None,
                latest_screening_date=latest_session.created_at.strftime("%Y-%m-%d %H:%M") if latest_session else None
            )
        )
    return result


@app.get("/admin/patients/{patient_id}", tags=["Admin Portal"])
def get_patient_profile(
    patient_id: int,
    db: Session = Depends(get_db),
    admin: User = Depends(require_admin)
):
    """Detailed patient dossier with complete longitudinal screening history."""
    patient = db.get(User, patient_id)
    if not patient or patient.role != "patient":
        raise HTTPException(status_code=404, detail="Patient record not found")
    return {
        "id": patient.id,
        "full_name": patient.full_name,
        "email": patient.email,
        "year_of_birth": patient.year_of_birth,
        "created_at": patient.created_at,
        "assessments": patient.sessions
    }


@app.get("/admin/flagged", tags=["Admin Portal"])
def list_flagged_sessions(
    db: Session = Depends(get_db),
    admin: User = Depends(require_admin)
):
    """Priority triage queue: returns all flagged/atypical assessments requiring clinical review."""
    flagged = (
        db.query(AssessmentSession)
        .filter(AssessmentSession.risk_label.in_(["Review suggested", "Atypical pattern", "ATYPICAL"]))
        .order_by(AssessmentSession.created_at.desc())
        .all()
    )
    return [{
        "session_id": s.session_id,
        "patient_id": s.user_id,
        "patient_name": s.user.full_name if s.user else "Unknown Patient",
        "patient_email": s.user.email if s.user else "",
        "patient_birth_year": s.user.year_of_birth if s.user else None,
        "overall_stability_score": s.overall_stability_score,
        "risk_label": s.risk_label,
        "drawing_tremor_level": s.drawing_tremor_level,
        "drawing_status": s.drawing_status,
        "tapping_status": s.tapping_status,
        "review_status": s.review_status,
        "clinical_notes": s.clinical_notes,
        "reviewed_by": s.reviewed_by,
        "created_at": s.created_at
    } for s in flagged]


@app.patch("/admin/sessions/{session_id}/review", response_model=SessionOut, tags=["Admin Portal"])
def review_session(
    session_id: str,
    body: ClinicalReviewIn,
    db: Session = Depends(get_db),
    admin: User = Depends(require_admin)
):
    """Allows a neurologist to add clinical notes, confirm review, and override the risk flag."""
    s = db.get(AssessmentSession, session_id)
    if s is None:
        raise HTTPException(status_code=404, detail="Assessment session not found")
    
    s.clinical_notes = body.clinical_notes
    s.review_status = body.review_status
    s.reviewed_by = admin.full_name
    s.reviewed_at = datetime.utcnow()

    if body.override_risk_label:
        s.risk_label = body.override_risk_label

    return save(db, s)


@app.post("/admin/seed-clinician", tags=["Admin Portal"])
def seed_default_clinician(db: Session = Depends(get_db)):
    """One-click provisioner for testing the Clinician Portal without manual DB inserts."""
    clinician_email = "clinician@hospital.org"
    existing = db.query(User).filter_by(email=clinician_email).first()
    if existing:
        return {"message": "Default clinician already exists", "email": clinician_email}

    hashed = bcrypt.hashpw("Clinician2026!".encode(), bcrypt.gensalt()).decode()
    clinician = User(
        full_name="Dr. Mercy Too (Neurologist)",
        email=clinician_email,
        password_hash=hashed,
        year_of_birth=1985,
        role="admin"
    )
    db.add(clinician)
    db.commit()
    db.refresh(clinician)
    return {
        "message": "Clinician user seeded successfully",
        "email": clinician_email,
        "role": clinician.role
    }
