#!/usr/bin/env python3
"""Project-local Superpowers SessionStart hook (Windows-friendly)."""

from __future__ import annotations

import json
from pathlib import Path

HOOKS_DIR = Path(__file__).resolve().parent
CURSOR_DIR = HOOKS_DIR.parent
SKILL_PATH = CURSOR_DIR / "skills" / "using-superpowers" / "SKILL.md"

try:
    using_superpowers_content = SKILL_PATH.read_text(encoding="utf-8")
except OSError as exc:
    using_superpowers_content = f"Error reading using-superpowers skill: {exc}"

session_context = (
    "<EXTREMELY_IMPORTANT>\n"
    "You have superpowers.\n\n"
    "**Below is the full content of your 'superpowers:using-superpowers' skill"
    " - your introduction to using skills. For all other skills, use the"
    " 'Skill' tool:**\n\n"
    f"{using_superpowers_content}\n"
    "</EXTREMELY_IMPORTANT>"
)

print(json.dumps({"additional_context": session_context}, ensure_ascii=False))
