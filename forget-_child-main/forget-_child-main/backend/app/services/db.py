import sqlite3
import threading
import time

from app.schemas import EventRecord


class Database:
    def __init__(self, path: str):
        self._conn = sqlite3.connect(path, check_same_thread=False)
        self._conn.row_factory = sqlite3.Row
        self._lock = threading.Lock()
        with self._lock:
            self._conn.executescript("""
            CREATE TABLE IF NOT EXISTS events(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              ts REAL NOT NULL, level INTEGER NOT NULL, reason TEXT,
              emotion TEXT, alone_seconds REAL, temp_c REAL, snapshot TEXT);
            CREATE TABLE IF NOT EXISTS fcm_tokens(
              token TEXT PRIMARY KEY, ts REAL NOT NULL);
            """)

    def add_event(self, level: int, reason: str, emotion: str, alone_s: float,
                  temp_c: float | None, snapshot: str | None) -> int:
        with self._lock:
            cur = self._conn.execute(
                "INSERT INTO events(ts,level,reason,emotion,alone_seconds,temp_c,snapshot) VALUES(?,?,?,?,?,?,?)",
                (time.time(), level, reason, emotion, alone_s, temp_c, snapshot))
            self._conn.commit()
            return cur.lastrowid

    def list_events(self, limit: int = 50) -> list[EventRecord]:
        with self._lock:
            rows = self._conn.execute(
                "SELECT * FROM events ORDER BY id DESC LIMIT ?", (limit,)).fetchall()
        return [EventRecord(id=r["id"], ts=r["ts"], level=r["level"], reason=r["reason"] or "",
                            emotion=r["emotion"] or "UNKNOWN", alone_seconds=r["alone_seconds"] or 0.0,
                            temp_c=r["temp_c"], has_snapshot=bool(r["snapshot"])) for r in rows]

    def upsert_token(self, token: str) -> None:
        with self._lock:
            self._conn.execute("INSERT OR REPLACE INTO fcm_tokens(token, ts) VALUES(?,?)", (token, time.time()))
            self._conn.commit()

    def all_tokens(self) -> list[str]:
        with self._lock:
            return [r["token"] for r in self._conn.execute("SELECT token FROM fcm_tokens")]
