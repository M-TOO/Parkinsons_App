from datetime import datetime
from sqlalchemy import Column, Integer, String, Float, DateTime, ForeignKey, CheckConstraint
from sqlalchemy.orm import declarative_base, relationship

Base = declarative_base()

class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    full_name = Column(String(100), nullable=False)
    email = Column(String(255), unique=True, index=True, nullable=False)
    password_hash = Column(String(255), nullable=False)
    year_of_birth = Column(
        Integer, 
        CheckConstraint("year_of_birth >= 1900 AND year_of_birth <= 2026"), 
        nullable=False
    )
    role = Column(String(20), default="patient", nullable=False)  # "patient" or "admin"
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    # Referential integrity: Links user to their sessions with cascade delete
    sessions = relationship("AssessmentSession", back_populates="user", cascade="all, delete-orphan")


class AssessmentSession(Base):
    __tablename__ = "assessment_sessions"

    session_id = Column(String(36), primary_key=True, index=True) # UUID from the mobile app
    user_id = Column(
        Integer, 
        ForeignKey("users.id", ondelete="CASCADE"), 
        nullable=False, 
        index=True
    )
    
    # Summary & Trend Metrics for history and reports
    overall_stability_score = Column(Float, nullable=True)
    session_status = Column(String(50), default="Completed")
    risk_label = Column(String(50), nullable=True)
    modules_completed = Column(Integer, default=4)
    
    # Detailed Metric Breakdown for the Report Screen
    tapping_stability_match = Column(Float, nullable=True)
    tapping_status = Column(String(50), nullable=True)
    drawing_tremor_level = Column(String(50), nullable=True)
    drawing_status = Column(String(50), nullable=True)
    reaction_speed_pct = Column(Float, nullable=True)
    spatial_precision_pct = Column(Float, nullable=True)
    coordination_status = Column(String(50), nullable=True)

    # Raw model outputs
    typing_pd_score = Column(Float, nullable=True)
    spiral_pd_score = Column(Float, nullable=True)
    wave_pd_score = Column(Float, nullable=True)
    model_version = Column(String(100), nullable=True)

    created_at = Column(DateTime, default=datetime.utcnow, nullable=False, index=True)

    # Referential integrity link back to User
    user = relationship("User", back_populates="sessions")
