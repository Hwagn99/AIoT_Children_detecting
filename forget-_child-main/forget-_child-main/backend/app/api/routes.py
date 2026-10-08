import os

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import FileResponse

from app.core.security import create_token, current_user, verify_credentials
from app.schemas import (DoorRequest, EventRecord, FcmTokenRequest, LoginRequest, OkResponse,
                         RuntimeSettings, SirenRequest, StatusResponse, TestAlertRequest,
                         TokenResponse)

router = APIRouter()


def _p(request: Request):
    return request.app.state.pipeline


# ---------- auth ----------
@router.post("/auth/login", response_model=TokenResponse)
def login(body: LoginRequest):
    if not verify_credentials(body.username, body.password):
        raise HTTPException(401, "Sai tên đăng nhập hoặc mật khẩu")
    return TokenResponse(access_token=create_token(body.username))


@router.post("/devices/fcm-token", response_model=OkResponse)
def register_fcm(body: FcmTokenRequest, request: Request, _: str = Depends(current_user)):
    request.app.state.db.upsert_token(body.token)
    return OkResponse()


# ---------- trạng thái / lịch sử ----------
@router.get("/status", response_model=StatusResponse)
def status(request: Request, _: str = Depends(current_user)):
    return _p(request).status()


@router.get("/events", response_model=list[EventRecord])
def events(request: Request, limit: int = 50, _: str = Depends(current_user)):
    return request.app.state.db.list_events(min(limit, 200))


@router.get("/snapshots/latest")
def latest_snapshot(request: Request, _: str = Depends(current_user)):
    path = _p(request).latest_snapshot_path()
    if not path or not os.path.exists(path):
        raise HTTPException(404, "Chưa có ảnh")
    return FileResponse(path, media_type="image/jpeg", headers={"Cache-Control": "no-store"})


# ---------- cấu hình ----------
@router.get("/settings", response_model=RuntimeSettings)
def get_settings(request: Request, _: str = Depends(current_user)):
    return request.app.state.runtime.get()


@router.put("/settings", response_model=RuntimeSettings)
def put_settings(body: RuntimeSettings, request: Request, _: str = Depends(current_user)):
    if not (body.grace_seconds < body.warn_seconds < body.emergency_seconds):
        raise HTTPException(422, "Cần grace < warn < emergency")
    return request.app.state.runtime.update(body)


# ---------- điều khiển thiết bị ----------
@router.post("/control/door", response_model=OkResponse)
def door(body: DoorRequest, request: Request, _: str = Depends(current_user)):
    request.app.state.mqtt.door(body.action)
    return OkResponse(detail=f"đã gửi lệnh {body.action}")


@router.post("/control/siren", response_model=OkResponse)
def siren(body: SirenRequest, request: Request, _: str = Depends(current_user)):
    request.app.state.mqtt.siren(body.on)
    return OkResponse(detail="bật còi" if body.on else "tắt còi")


@router.post("/control/silence", response_model=OkResponse)
def silence(request: Request, _: str = Depends(current_user)):
    _p(request).silence()
    return OkResponse(detail="đã tắt còi")


@router.post("/control/test-alert", response_model=OkResponse)
def test_alert(body: TestAlertRequest, request: Request, _: str = Depends(current_user)):
    _p(request).force_level(body.level)
    return OkResponse(detail=f"test mức {body.level}")
