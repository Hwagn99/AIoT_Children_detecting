"""JWT đơn giản cho app Android."""
import hmac
import time

import jwt
from fastapi import Depends, HTTPException, WebSocket, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.config import settings

_bearer = HTTPBearer(auto_error=False)
ALGO = "HS256"


def verify_credentials(username: str, password: str) -> bool:
    # TODO: thay bằng bảng user + băm mật khẩu (bcrypt/argon2) khi làm thật
    return (hmac.compare_digest(username, settings.app_username)
            and hmac.compare_digest(password, settings.app_password))


def create_token(username: str) -> str:
    now = int(time.time())
    payload = {"sub": username, "iat": now, "exp": now + settings.jwt_expire_minutes * 60}
    return jwt.encode(payload, settings.jwt_secret, algorithm=ALGO)


def decode_token(token: str) -> str:
    try:
        return jwt.decode(token, settings.jwt_secret, algorithms=[ALGO])["sub"]
    except jwt.PyJWTError as e:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Token không hợp lệ") from e


async def current_user(cred: HTTPAuthorizationCredentials | None = Depends(_bearer)) -> str:
    if cred is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Thiếu token")
    return decode_token(cred.credentials)


def ws_user(ws: WebSocket) -> str:
    token = ws.query_params.get("token")
    if not token:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Thiếu token")
    return decode_token(token)
