from __future__ import annotations

import sys
from pathlib import Path


def bootstrap_backend_path() -> Path:
    current = Path(__file__).resolve()
    candidates = [
        current.parents[1],
        current.parents[2] / "backend",
        Path.cwd(),
        Path.cwd() / "backend",
    ]

    for candidate in candidates:
        if (candidate / "app").is_dir():
            candidate_path = str(candidate)
            if candidate_path not in sys.path:
                sys.path.insert(0, candidate_path)
            return candidate

    raise RuntimeError("Cannot find backend app package. Run from repo root or backend container.")


def default_data_dir() -> Path:
    current = Path(__file__).resolve()
    if current.parents[1].name == "bigdata":
        return current.parents[1] / "data"
    return current.parents[1] / "data"


def positive_int(value: str) -> int:
    parsed = int(value)
    if parsed <= 0:
        raise ValueError("value must be positive")
    return parsed
