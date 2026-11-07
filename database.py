import os
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

# Set DATABASE_URL in production; the default is local dev (Laragon/XAMPP, root, no password).
DATABASE_URL = os.environ.get("DATABASE_URL", "mysql+pymysql://root:@localhost:3306/parkinsons_db")

engine = create_engine(DATABASE_URL, pool_pre_ping=True)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
