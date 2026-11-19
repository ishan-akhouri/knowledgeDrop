"""Local embedding service for KnowledgeDrop.

Wraps a sentence-transformers model behind a tiny HTTP API so the Java backend never loads a
model itself. The model is loaded once at startup.

    POST /embed   {"texts": ["a", "b"]}  ->  {"model": ..., "dimension": 384, "embeddings": [[...], [...]]}
    GET  /health  ->  {"status": "ok", "model": ..., "dimension": 384}

Vectors are L2-normalized, so cosine similarity equals the dot product.
"""

import os

from fastapi import FastAPI
from pydantic import BaseModel, Field
from sentence_transformers import SentenceTransformer

MODEL_NAME = os.environ.get("EMBEDDING_MODEL", "all-MiniLM-L6-v2")
MAX_TEXTS_PER_REQUEST = 256

model = SentenceTransformer(MODEL_NAME)
DIMENSION = model.get_sentence_embedding_dimension()

app = FastAPI(title="KnowledgeDrop embedding service")


class EmbedRequest(BaseModel):
    texts: list[str] = Field(min_length=1, max_length=MAX_TEXTS_PER_REQUEST)


class EmbedResponse(BaseModel):
    model: str
    dimension: int
    embeddings: list[list[float]]


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "model": MODEL_NAME, "dimension": DIMENSION}


@app.post("/embed", response_model=EmbedResponse)
def embed(request: EmbedRequest) -> EmbedResponse:
    vectors = model.encode(
        request.texts,
        batch_size=32,
        normalize_embeddings=True,
        convert_to_numpy=True,
    )
    return EmbedResponse(
        model=MODEL_NAME,
        dimension=DIMENSION,
        embeddings=vectors.tolist(),
    )
