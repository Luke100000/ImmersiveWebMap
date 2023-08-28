import base64
import json
import os
import shutil
import uuid
from io import BytesIO
from typing import List

import imageio.v2 as imageio
import numpy as np
from asyncache import cached
from cachetools import TTLCache
from databases import Database
from fastapi.templating import Jinja2Templates
from prometheus_client import CollectorRegistry, multiprocess
from pydantic import BaseModel
from starlette.middleware.cors import CORSMiddleware
from starlette.middleware.gzip import GZipMiddleware
from starlette.responses import JSONResponse, Response
from starlette.staticfiles import StaticFiles

# Setup prometheus for multiprocessing
prom_dir = (
    os.environ["PROMETHEUS_MULTIPROC_DIR"]
    if "PROMETHEUS_MULTIPROC_DIR" in os.environ
    else None
)
if prom_dir is not None:
    shutil.rmtree(prom_dir, ignore_errors=True)
    os.makedirs(prom_dir, exist_ok=True)
    registry = CollectorRegistry()
    multiprocess.MultiProcessCollector(registry)

from fastapi import FastAPI, Request
from prometheus_fastapi_instrumentator import Instrumentator

app = FastAPI()

app.add_middleware(GZipMiddleware, minimum_size=4096, compresslevel=6)

# Allow CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Open Database
database = Database("sqlite:///database.db")

# Prometheus integration
instrumentator = Instrumentator().instrument(app)

if os.path.exists("test/results.json"):
    with open("test/results.json") as results_file:
        recommended_converter = json.load(results_file)
else:
    recommended_converter = {}

templates = Jinja2Templates(directory="templates")
app.mount("/static", StaticFiles(directory="static"), name="static")


async def setup():
    await database.execute("PRAGMA foreign_keys=ON")

    await database.execute(
        """
        CREATE TABLE IF NOT EXISTS servers (
           oid INTEGER PRIMARY KEY AUTOINCREMENT,
           token TEXT,
           meta TEXT
        )
    """
    )

    await database.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS servers_oid on servers (oid)"
    )

    await database.execute(
        """
        CREATE TABLE IF NOT EXISTS dimensions (
           oid INTEGER PRIMARY KEY AUTOINCREMENT,
           server INTEGER,
           key TEXT,
           meta TEXT,
           FOREIGN KEY(server) REFERENCES servers(oid) ON DELETE CASCADE
        )
    """
    )

    await database.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS dimensions_oid on dimensions (oid)"
    )

    await database.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS dimensions_server_key on dimensions (server, key)"
    )

    # This table is used for the IDE as a reference
    await ensure_chunk_table("missing_value")


@app.on_event("startup")
async def startup():
    instrumentator.expose(app)
    await setup()


class ChunkPayload(BaseModel):
    x: int
    y: int = -9999
    z: int
    data: str
    meta: str

    @property
    def payload(self) -> bytes:
        return base64.b64decode(self.data)


async def get_server(server: int):
    return await database.fetch_one(
        "SELECT * FROM servers WHERE oid = :oid AND token = :token",
        {
            "server": server,
        },
    )


async def is_authorized(server: int, token: str):
    return (
        await database.fetch_one(
            "SELECT * FROM servers WHERE oid = :server AND token = :token",
            {
                "server": server,
                "token": token,
            },
        )
    ) is not None


def get_error(status: int, message: str) -> JSONResponse:
    """
    Wrap a status and message into a JSON
    """
    return JSONResponse(status_code=status, content={"message": message})


async def get_dimension_identifier(server: int, dimension: str):
    identifier = await database.fetch_one(
        "SELECT oid FROM dimensions WHERE server = :server AND key = :key",
        {"server": server, "key": dimension},
    )

    if identifier is None:
        await database.execute(
            "INSERT INTO dimensions (server, key, meta) VALUES (:server, :key, :meta)",
            {"server": server, "key": dimension, "meta": "{}"},
        )
        return await get_dimension_identifier(server, dimension)
    else:
        return identifier[0]


async def ensure_chunk_table(identifier: int):
    await database.execute(
        f"""
        CREATE TABLE IF NOT EXISTS chunks_{identifier} (
           oid INTEGER PRIMARY KEY AUTOINCREMENT,
           x INTEGER,
           y INTEGER,
           z INTEGER,
           color BLOB,
           meta TEXT
        )
    """
    )

    await database.execute(
        f"CREATE UNIQUE INDEX IF NOT EXISTS chunks_{identifier}_xyz on chunks_{identifier} (x, y, z)"
    )


@app.get("/map/{server}/{dimension}")
async def index(request: Request, server: int, dimension: str):
    dimensions = await database.fetch_all(
        "SELECT key FROM dimensions WHERE server = :server", {"server": server}
    )

    meta = await database.fetch_one(
        "SELECT meta FROM servers WHERE oid = :server", {"server": server}
    )

    return templates.TemplateResponse(
        "index.html",
        {
            "request": request,
            "server": server,
            "dimension": dimension,
            "dimensions": [d[0] for d in dimensions],
            "serverName": json.loads(meta[0])["name"]
        },
    )


@app.get("/v1/auth")
async def auth(server: int = -1, token: str = "none"):
    """
    Generates a server id and authorization token, or verify existing authorization and generate a new one if invalid
    :param server: The server id
    :param token: The authorization token
    """

    if server <= 0 or not await is_authorized(server, token):
        token = uuid.uuid4().hex
        server = await database.execute(
            "INSERT INTO servers (token, meta) VALUES (:token, :meta)",
            {"token": token, "meta": "{}"},
        )

    return {"token": token, "server": server}


@app.put("/v1/meta/{server}")
async def update_server(server: int, token: str, meta: str):
    """
    Updates a server
    :param server: The server id
    :param token: The authorization token
    :param meta: Json metadata
    """

    if await is_authorized(server, token):
        await database.execute(
            "UPDATE servers SET meta = :meta WHERE oid = :server",
            {"meta": meta, "server": server},
        )

        return {"message": "success"}
    else:
        return get_error(401, "Token or server invalid")


@app.get("/v1/meta/{server}")
async def get_server(server: int):
    meta = await database.execute(
        "SELECT meta FROM servers WHERE oid = :server",
        {"server": server},
    )

    return {"meta": json.loads(meta[0])}


@app.get("/v1/meta/{server}/{dimension}")
async def get_dimension(server: int, dimension: str):
    meta = await database.fetch_one(
        "SELECT meta FROM dimensions WHERE server = :server AND key = :key",
        {"server": server, "key": dimension},
    )

    return {"meta": json.loads(meta[0])}


@app.put("/v1/meta/{server}/{dimension}")
async def update_dimension(server: int, dimension: str, token: str, meta: str):
    """
    Updates a server
    :param server: The server id
    :param dimension: The dimension key
    :param token: The authorization token
    :param meta: Json metadata
    """

    if await is_authorized(server, token):
        await database.execute(
            "UPDATE dimensions SET meta = :meta WHERE server = :server AND key = :key",
            {"meta": meta, "server": server, "key": dimension},
        )

        return {"message": "success"}
    else:
        return get_error(401, "Token or server invalid")


@app.post("/v1/chunks/{server}/{dimension}")
async def post_chunks(
    server: int,
    dimension: str,
    token: str,
    payload: List[ChunkPayload],
):
    if await is_authorized(server, token):
        identifier = await get_dimension_identifier(server, dimension)
        await ensure_chunk_table(identifier)

        values = []
        for chunk in payload:
            values.append(
                {
                    "x": chunk.x,
                    "y": chunk.y,
                    "z": chunk.z,
                    "color": chunk.payload,
                    "meta": chunk.meta,
                }
            )

        await database.execute_many(
            f"INSERT OR REPLACE INTO chunks_{identifier} (x, y, z, color, meta) VALUES (:x, :y, :z, :color, :meta)",
            values,
        )


@cached(cache=TTLCache(maxsize=1024, ttl=60))
async def get_chunk_png(
    server: int,
    dimension: str,
    x: int,
    z: int,
    w: int = 1,
    h: int = 1,
    scale: int = 1,
    y: int = -9999,
):
    identifier = await get_dimension_identifier(server, dimension)

    tile_size = 16 // scale
    result = np.zeros((w * tile_size, h * tile_size, 3), np.uint8)
    result[:, :, 0] = 213
    result[:, :, 1] = 190
    result[:, :, 2] = 149

    chunks = await database.fetch_all(
        f"SELECT color, x, z FROM chunks_{identifier} WHERE x >= :x0 AND x < :x1 AND y = :y AND z >= :z0 AND z < :z1",
        {"x0": x, "x1": x + w, "y": y, "z0": z, "z1": z + h},
    )

    for chunk in chunks:
        color = imageio.imread(BytesIO(chunk[0]), format="png")[:, :, :3]

        if scale > 1:
            # todo
            color = color[::scale, ::scale]

        result[
            (chunk[2] - z) * tile_size : (chunk[2] - z + 1) * tile_size,
            (chunk[1] - x) * tile_size : (chunk[1] - x + 1) * tile_size,
        ] = color

    return imageio.imwrite("<bytes>", result, format="png")


@app.get("/v1/chunk/{server}/{dimension}")
async def get_chunk(
    server: int,
    dimension: str,
    x: int,
    z: int,
    w: int = 1,
    h: int = 1,
    scale: int = 1,
    y: int = -9999,
):
    return Response(await get_chunk_png(server, dimension, x, z, w, h, scale, y))
