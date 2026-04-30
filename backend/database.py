import aiosqlite
import os

DB_PATH = os.path.join(os.path.dirname(__file__), "freeze_server.db")


async def init_db():
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute("""
            CREATE TABLE IF NOT EXISTS detection_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                filename TEXT,
                detections TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        await db.commit()


async def get_db():
    return aiosqlite.connect(DB_PATH)
