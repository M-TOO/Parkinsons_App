from sqlalchemy import Column, DateTime, Float, ForeignKey, Integer, String, func
from sqlalchemy.orm import declarative_base, relationship

Base = declarative_base()


class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, autoincrement=True)
    full_name = Column(String(100), nullable=False)
    email = Column(String(255), unique=True, index=True, nullable=False)
    password_hash = Column(String(255), nullable=False)
    year_of_birth = Column(Integer, nullable=False)  # range validated in schemas.py (no stale hard-coded year)
    created_at = Column(DateTime, server_default=func.now(), nullable=False)

    sessions = relationship("AssessmentSession", back_populates="user", cascade="all, delete-orphan")


class AssessmentSession(Base):
    __tablename__ = "assessment_sessions"

    session_id = Column(String(36), primary_key=True)  # UUID from the mobile app
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)

    overall_stability_score = Column(Float, nullable=True)
    session_status = Column(String(50), default="Completed")
    risk_label = Column(String(50), nullable=True)
    modules_completed = Column(Integer, default=4)

    tapping_stability_match = Column(Float, nullable=True)
    tapping_status = Column(String(50), nullable=True)
    drawing_tremor_level = Column(String(50), nullable=True)
    drawing_status = Column(String(50), nullable=True)
    reaction_speed_pct = Column(Float, nullable=True)
    spatial_precision_pct = Column(Float, nullable=True)
    coordination_status = Column(String(50), nullable=True)

    # NEW: raw model outputs (labels above are derived from these and can be recomputed later)
    typing_pd_score = Column(Float, nullable=True)
    spiral_pd_score = Column(Float, nullable=True)
    wave_pd_score = Column(Float, nullable=True)
    model_version = Column(String(100), nullable=True)

    created_at = Column(DateTime, server_default=func.now(), nullable=False, index=True)

    user = relationship("User", back_populates="sessions")
