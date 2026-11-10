import os
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

# Default: MySQL for local production / XAMPP, with SQLite fallback if DATABASE_URL specified
DATABASE_URL = os.environ.get("DATABASE_URL", "mysql+pymysql://root:@localhost:3306/parkinsons_db")

# SQLite needs check_same_thread=False
connect_args = {"check_same_thread": False} if DATABASE_URL.startswith("sqlite") else {}

engine = create_engine(DATABASE_URL, pool_pre_ping=True, connect_args=connect_args)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
