from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

# MySQL Database connection URL
# Format: mysql+pymysql://username:password@host:port/database_name
# Replace 'root' and your password if you have one set up in MySQL (e.g., Laragon/XAMPP)
SQLALCHEMY_DATABASE_URL = "mysql+pymysql://root:@localhost:3306/parkinsons_db"

# Create the SQLAlchemy engine that communicates with your local MySQL server
engine = create_engine(SQLALCHEMY_DATABASE_URL)

# Create a session factory for database transactions
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

# Dependency helper used in FastAPI routes to manage database sessions safely
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()