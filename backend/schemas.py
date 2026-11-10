from datetime import date, datetime
from pydantic import BaseModel, ConfigDict, EmailStr, Field, field_validator


class UserCreate(BaseModel):
    full_name: str = Field(min_length=2, max_length=100)
    email: EmailStr
    password: str = Field(min_length=8, max_length=72)
    year_of_birth: int = Field(ge=1900)

    @field_validator("year_of_birth")
    @classmethod
    def not_in_future(cls, v: int) -> int:
        if v > date.today().year:
            raise ValueError("year_of_birth cannot be in the future")
        return v


class UserLogin(BaseModel):
    email: EmailStr
    password: str


class UserResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    full_name: str
    email: EmailStr
    year_of_birth: int


class TokenOut(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserResponse


class TypingIn(BaseModel):
    # excerpts[i] = list of [press_ms, release_ms] key events
    excerpts: list[list[tuple[int, int]]] = Field(max_length=30)


class MotorIn(BaseModel):
    reaction_speed_pct: float | None = Field(None, ge=0, le=100)
    spatial_precision_pct: float | None = Field(None, ge=0, le=100)
    coordination_status: str | None = Field(None, max_length=50)
    modules_completed: int | None = Field(None, ge=0, le=10)


class SessionOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    session_id: str
    overall_stability_score: float | None
    session_status: str | None
    risk_label: str | None
    modules_completed: int | None
    tapping_stability_match: float | None
    tapping_status: str | None
    drawing_tremor_level: str | None
    drawing_status: str | None
    reaction_speed_pct: float | None
    spatial_precision_pct: float | None
    coordination_status: str | None
    created_at: datetime
