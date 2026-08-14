#!/usr/bin/env python3
"""
Extracts every REST endpoint from the Spring Boot controllers and writes
docs/api-list.json.

Usage:
    python scripts/extract-api-list.py

Output shape (one entry per endpoint):
{
  "method": "GET",
  "path": "/api/users",
  "controller": "UserController.java",
  "handler": "listUsers",
  "authorization": "hasAuthority('USER_MANAGE')" | ""
}
"""
import os
import re
import json
import glob
import datetime

BACKEND = os.path.join(os.path.dirname(__file__), "..", "backend", "src", "main", "java")
OUT = os.path.join(os.path.dirname(__file__), "..", "docs", "api-list.json")

# Only method-level verbs count as endpoints. @RequestMapping at class level
# is the prefix and is handled separately; matching it here too would invent
# phantom endpoints like "/api/foo/api/foo".
MAPPING_RE = re.compile(r"@(Get|Post|Put|Delete|Patch)Mapping\b")


def class_prefix(head):
    m = re.search(r'@RequestMapping\(\s*(?:value\s*=\s*)?["\']([^"\']+)["\']', head)
    return m.group(1).rstrip("/") if m else ""


def first_quoted(annot):
    m = re.search(r'(?:value\s*=\s*)?["\']([^"\']*)["\']', annot)
    return m.group(1) if m else ""


def find_preauthorize(lines, up_to):
    """Return the first @PreAuthorize expression found in the window before
    this mapping annotation (15 lines is plenty for typical formatting)."""
    for k in range(max(0, up_to - 15), up_to):
        if "@PreAuthorize" in lines[k]:
            joined = "".join(lines[k:up_to + 1])
            m = re.search(r'@PreAuthorize\(\s*"([^"]*)"', joined)
            if m:
                return m.group(1)
    return ""


def find_handler(lines, start, limit=8):
    for k in range(start, min(len(lines), start + limit)):
        if "public" in lines[k] or "protected" in lines[k]:
            m = re.search(r"\b([a-zA-Z_][a-zA-Z0-9_]*)\s*\(", lines[k])
            if m:
                return m.group(1)
    return ""


def main():
    endpoints = []
    for path in glob.glob(os.path.join(BACKEND, "**", "*.java"), recursive=True):
        with open(path, encoding="utf-8", errors="replace") as f:
            text = f.read()
        if "@RestController" not in text and "@Controller" not in text:
            continue
        lines = text.splitlines()
        cls_idx = text.find("class ")
        prefix = class_prefix(text[:cls_idx]) if cls_idx > 0 else ""

        i = 0
        n = len(lines)
        while i < n:
            line = lines[i]
            m = MAPPING_RE.search(line)
            if m:
                annot = line[m.start():]
                j = i
                depth = annot.count("(") - annot.count(")")
                while depth > 0 and j < n - 1:
                    j += 1
                    annot += "\n" + lines[j]
                    depth += lines[j].count("(") - lines[j].count(")")
                verb = m.group(1).upper()
                rel = first_quoted(annot).lstrip("/")
                full = f"{prefix}/{rel}" if rel else prefix
                if not full:
                    # No path anywhere (e.g. @GetMapping() on an abstract base)
                    # — not a callable endpoint.
                    i = j
                    continue
                endpoints.append({
                    "method": verb,
                    "path": full,
                    "controller": os.path.basename(path),
                    "handler": find_handler(lines, i),
                    "authorization": find_preauthorize(lines, i),
                })
                i = j
            i += 1

    # Sort by path then method for a stable, readable listing.
    endpoints.sort(key=lambda e: (e["path"], e["method"]))
    doc = {
        "project": "Pixous HR Portal",
        "description": "Complete REST API inventory extracted from the Spring Boot controllers.",
        "generated": datetime.date.today().isoformat(),
        "baseUrl": "http://16.192.105.61",
        "count": len(endpoints),
        "endpoints": endpoints,
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(doc, f, indent=2, ensure_ascii=False)
    print(f"Wrote {len(endpoints)} endpoints -> {OUT}")


if __name__ == "__main__":
    main()
