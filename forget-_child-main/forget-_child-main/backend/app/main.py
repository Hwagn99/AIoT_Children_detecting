"""Chạy:  uvicorn app.main:app --host 0.0.0.0 --port 8000"""
import asyncio
import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api import routes, ws
from app.config import settings
from app.core.camera import Camera
from app.core.detector import ChildAdultDetector
from app.core.state import RuntimeSettingsStore, Telemetry
from app.services.db import Database
from app.services.mqtt_client import MqttClient
from app.services.notifier import Notifier
from app.services.pipeline import Pipeline

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")


@asynccontextmanager
async def lifespan(app: FastAPI):
    os.makedirs(settings.data_dir, exist_ok=True)

    telemetry = Telemetry()
    db = Database(settings.db_path)
    runtime = RuntimeSettingsStore(settings.runtime_settings_path)
    camera = Camera(settings.camera_source)
    detector = ChildAdultDetector(settings.yolo_weights, settings.yolo_conf, settings.yolo_imgsz)
    mqtt = MqttClient(settings, telemetry)
    notifier = Notifier(settings, db)
    pipeline = Pipeline(settings, runtime, camera, detector, mqtt, telemetry, notifier, db)

    app.state.db, app.state.runtime = db, runtime
    app.state.mqtt, app.state.pipeline = mqtt, pipeline

    camera.start()
    mqtt.start()
    pipe_task = asyncio.create_task(pipeline.run())
    ws_task = asyncio.create_task(ws.broadcast_loop(lambda: pipeline.status().model_dump_json()))
    try:
        yield
    finally:
        pipeline.stop()
        await ws.cancel(ws_task)
        await ws.cancel(pipe_task)
        mqtt.stop()
        camera.stop()


app = FastAPI(title="Child Safety Car API", version="0.1.0", lifespan=lifespan)
app.include_router(routes.router)
app.include_router(ws.router)


@app.get("/health")
def health():
    return {"ok": True}
