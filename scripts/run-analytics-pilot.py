#!/usr/bin/env python3
"""Run a reproducible 500-book Analytics V1 runtime pilot.

This is a model/runtime and pipeline-capacity gate. It intentionally does not
write to PostgreSQL, object storage, or production data.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import time
from pathlib import Path
from urllib.request import Request, urlopen


def request_json(base_url: str, path: str, payload: dict) -> dict:
    body = json.dumps(payload).encode("utf-8")
    request = Request(base_url.rstrip("/") + path, data=body,
                      headers={"Content-Type": "application/json"}, method="POST")
    with urlopen(request, timeout=600) as response:
        return json.loads(response.read())


def fixture(index: int, language: str) -> str:
    if language == "pt":
        sentence = ("A pesquisadora atravessou a praça silenciosa enquanto a tempestade se aproximava. "
                    "Ela perguntou: — Encontraremos a saída antes do amanhecer? "
                    "O mapa antigo revelou uma passagem escondida, com café, música e memória.")
    else:
        sentence = ("The researcher crossed the silent square while the storm approached. "
                    "She asked: — Will we find the exit before dawn? "
                    "The old map revealed a hidden passage, with café, music, and memory.")
    return f"Book fixture {index}. " + " ".join([sentence] * 8)


def codepoint_candidates(text: str) -> int:
    return max(0, len(text.split()) // 20)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:18093")
    parser.add_argument("--books", type=int, default=500)
    parser.add_argument("--seed", type=int, default=1337)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.books < 500:
        raise SystemExit("the V1 pilot requires at least 500 books")

    started = time.perf_counter()
    books = [(i, "en" if i % 2 == 0 else "pt", fixture(i, "en" if i % 2 == 0 else "pt"))
             for i in range(args.books)]
    structural_candidates = sum(codepoint_candidates(text) for _, _, text in books)
    embedding_valid = 0
    embedding_failures = []
    embedding_seconds = 0.0
    for offset in range(0, len(books), 32):
        batch = books[offset:offset + 32]
        batch_started = time.perf_counter()
        result = request_json(args.base_url, "/v1/embeddings",
                              {"texts": [text for _, _, text in batch], "max_tokens": 8192})
        embedding_seconds += time.perf_counter() - batch_started
        vectors = result.get("vectors", [])
        if result.get("status") != "VALID" or result.get("dimension") != 1024 or len(vectors) != len(batch):
            embedding_failures.append({"offset": offset, "status": result.get("status", "MISSING")})
            continue
        for vector in vectors:
            norm = math.sqrt(sum(float(value) ** 2 for value in vector))
            if len(vector) != 1024 or abs(norm - 1.0) > 1e-5:
                embedding_failures.append({"offset": offset, "status": "INVALID_VECTOR"})
            else:
                embedding_valid += 1

    sample = books[:20] + books[len(books) // 2:len(books) // 2 + 20]
    language_rows, linguistic_rows, nli_rows = [], [], []
    for _, language, text in sample:
        language_rows.append(request_json(args.base_url, "/v1/language", {"text": text}))
        linguistic_rows.append(request_json(args.base_url, "/v1/linguistic", {"language": language, "text": text}))
        nli_rows.append(request_json(args.base_url, "/v1/nli", {
            "text": text, "hypothesis": "The passage contains an external event."}))

    def valid_language(row, expected):
        return row.get("status") == "VALID" and row.get("language") == expected and 0 <= row.get("confidence", -1) <= 1

    language_valid = sum(valid_language(row, language) for row, (_, language, _) in zip(language_rows, sample))
    linguistic_valid = sum(row.get("status") == "VALID" and bool(row.get("values")) for row in linguistic_rows)
    nli_valid = 0
    for row in nli_rows:
        scores = [row.get("entailment", -1), row.get("neutral", -1), row.get("contradiction", -1)]
        if row.get("status") == "VALID" and all(0 <= score <= 1 for score in scores) and abs(sum(scores) - 1) < 1e-5:
            nli_valid += 1

    duration = time.perf_counter() - started
    result = {
        "pilot": "analytics-v1-runtime",
        "status": "GO_RUNTIME_CAPACITY" if embedding_valid == args.books and language_valid == len(sample) and linguistic_valid == len(sample) and nli_valid == len(sample) else "NO_GO",
        "quality_gate": "NOT_ASSESSED",
        "seed": args.seed,
        "books": args.books,
        "languages": {"en": sum(language == "en" for _, language, _ in books), "pt": sum(language == "pt" for _, language, _ in books)},
        "structural_candidates": structural_candidates,
        "embedding_valid": embedding_valid,
        "embedding_failures": embedding_failures,
        "sample_size": len(sample),
        "language_valid": language_valid,
        "linguistic_valid": linguistic_valid,
        "nli_valid": nli_valid,
        "embedding_seconds": round(embedding_seconds, 3),
        "elapsed_seconds": round(duration, 3),
        "books_per_second": round(args.books / max(duration, 1e-9), 3),
        "dataset_sha256": hashlib.sha256("\n".join(f"{i}:{language}:{text}" for i, language, text in books).encode()).hexdigest(),
        "writes_to_production": False,
        "model_download": False,
    }
    rendered = json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)
    if result["status"] != "GO_RUNTIME_CAPACITY":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
