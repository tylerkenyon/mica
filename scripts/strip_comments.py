#!/usr/bin/env python3
"""Trim verbose Javadoc and inline // comments from Java source.

Rules:
  - /** ... */ blocks: drop them.
  - /* ... */ non-javadoc blocks: drop them too. (mixins can be re-added by hand.)
  - Inline `//` line comments: drop them; preserve leading whitespace.
  - Preserve the rest of the file verbatim.

Re-running is idempotent.
"""
from __future__ import annotations
import re
import sys
from pathlib import Path

BLOCK_COMMENT_RE = re.compile(r"/\*(?:[^*]|\*(?!/))*?\*/", re.DOTALL)
LINE_COMMENT_RE = re.compile(r"^(\s*)//.*$", re.MULTILINE)


def transform(text: str) -> str:
    text = BLOCK_COMMENT_RE.sub("", text)
    text = LINE_COMMENT_RE.sub(r"\1", text)
    return text


def main(argv: list[str]) -> int:
    root = Path(argv[1] if len(argv) > 1 else "src")
    if not root.exists():
        print(f"root not found: {root}", file=sys.stderr)
        return 2
    count = 0
    for path in root.rglob("*.java"):
        original = path.read_text(encoding="utf-8")
        cleaned = transform(original)
        if cleaned != original:
            path.write_text(cleaned, encoding="utf-8")
            count += 1
    print(f"trimmed {count} files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
