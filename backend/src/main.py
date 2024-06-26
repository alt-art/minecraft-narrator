import asyncio
import json
from contextlib import asynccontextmanager
import sys

import fastapi

from fastapi.staticfiles import StaticFiles
from loguru import logger
from src.handler import event_handler
from src.models import Config, Event, IncomingEvent
from src.prompts import prompt_manager
from src.websocket import ws
from src.dashboard import start_dashboard
from src.components.tabs.logs import dashboard_sink
from src.voice import voice

# TODO: Add option to enable debug logs to stdout with backtrace and diagnose when developing
logger.remove()  # Remove default logger
logger.add(dashboard_sink, level="INFO", backtrace=False, diagnose=False)
logger.add(sys.stdout, level="INFO", backtrace=False, diagnose=False)
logger.add("logs/{time}.log", rotation="1 day", level="DEBUG", compression="zip")


@asynccontextmanager
async def lifespan_handler(_app: fastapi.FastAPI):
    logger.info("Starting server")
    start_dashboard(asyncio.get_event_loop())
    logger.info("Open http://127.0.0.1:5000/speech/microphone.html to start capturing mic audio")
    yield
    logger.info("Stopping server")


app = fastapi.FastAPI(lifespan=lifespan_handler)


@app.websocket("/ws")
async def websocket_endpoint(websocket: fastapi.WebSocket):
    logger.info(f"New connection: {websocket.client}")
    await ws.connect(websocket)
    try:
        while True:
            logger.debug("Waiting for websocket data")
            json_data = await websocket.receive_json()
            logger.debug(f"Received data from websocket: {json_data!r}")
            # TODO: Obfuscate sensitive data in logs (e.g. tokens)
            # TODO: Add validation for incoming data

            incoming_event: IncomingEvent = IncomingEvent(**json_data)
            logger.info(f"Received event of type {incoming_event.event!r}")

            match incoming_event.event:
                case Event.CONFIG:
                    config: Config = json.loads(incoming_event.data, object_hook=lambda d: Config(**d))
                    event_handler.handle_config_event(config)
                case Event.VOICE_ACTIVATE:
                    await voice.handle_voice_activate()
                case Event.VOICE_COMPLETE:
                    await voice.handle_voice_complete(incoming_event)
                case Event.SET_SYSTEM:
                    prompt_manager.set_current_prompt(incoming_event.data, False)
                case Event.CUSTOM_TTS:
                    event_handler.handle_custom_tts(incoming_event)
                case _:
                    logger.info(f"Incoming event data: {incoming_event.data!r}")
                    await event_handler.handle_game_event(incoming_event)

    except Exception as e:
        logger.info(f"Client {websocket.client} disconnected")
        ws.disconnect(websocket)
        if not isinstance(e, fastapi.WebSocketDisconnect):
            raise e


app.mount("/speech", StaticFiles(directory="src/speech"), name="speech")
app.add_websocket_route("/mic", voice.handle_websocket_microphone)
