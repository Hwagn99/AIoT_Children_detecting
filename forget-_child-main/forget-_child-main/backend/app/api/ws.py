import asyncio
import contextlib

from fastapi import APIRouter, HTTPException, WebSocket, WebSocketDisconnect

from app.core.security import ws_user

router = APIRouter()


class ConnectionManager:
    def __init__(self):
        self.clients: set[WebSocket] = set()

    async def broadcast(self, text: str) -> None:
        dead = []
        for ws in list(self.clients):
            try:
                await ws.send_text(text)
            except Exception:  # noqa: BLE001
                dead.append(ws)
        for ws in dead:
            self.clients.discard(ws)


manager = ConnectionManager()


@router.websocket("/ws")
async def ws_endpoint(ws: WebSocket):
    try:
        ws_user(ws)
    except HTTPException:
        await ws.close(code=4401)
        return
    await ws.accept()
    manager.clients.add(ws)
    try:
        while True:
            await ws.receive_text()          # giữ kết nối; app không cần gửi gì
    except WebSocketDisconnect:
        pass
    finally:
        manager.clients.discard(ws)


async def broadcast_loop(get_status_json, period_s: float = 1.0) -> None:
    while True:
        if manager.clients:
            await manager.broadcast(get_status_json())
        await asyncio.sleep(period_s)


async def cancel(task: asyncio.Task) -> None:
    task.cancel()
    with contextlib.suppress(asyncio.CancelledError):
        await task
