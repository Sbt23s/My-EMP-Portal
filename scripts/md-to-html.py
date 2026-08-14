#!/usr/bin/env python3
"""
Converts the client-submission documents (REQUIREMENTS.md, UNIT-TESTING.md and
docs/api-list.json) into styled HTML files, ready for headless-Chromium PDF export.

Usage:
    python scripts/md-to-html.py

Outputs (docs/downloads/):
    Pixous_HR_Requirements_v1.0.html
    Pixous_HR_Unit_Testing_v1.0.html
    Pixous_HR_API_List_v1.0.html
"""
import os
import re
import json
import base64
import html as html_mod

ROOT = os.path.join(os.path.dirname(__file__), "..")
DOCS = os.path.join(ROOT, "docs")
OUT = os.path.join(DOCS, "downloads")

LOGO = os.path.join(ROOT, "web", "public", "pixous-favicon.png")
LOGO_B64 = None
if os.path.exists(LOGO):
    with open(LOGO, "rb") as f:
        LOGO_B64 = base64.b64encode(f.read()).decode()

CSS = """
@page { size: A4; margin: 18mm 14mm; }
* { box-sizing: border-box; }
body { font-family: 'Segoe UI', Calibri, Arial, sans-serif; font-size: 10.5pt;
       color: #1e293b; line-height: 1.45; margin: 0; }
h1 { font-size: 20pt; color: #4f46e5; border-bottom: 2px solid #4f46e5;
     padding-bottom: 6px; margin: 6px 0 14px; }
h2 { font-size: 15pt; color: #1e293b; margin: 20px 0 8px;
     border-left: 4px solid #4f46e5; padding-left: 8px; }
h3 { font-size: 12pt; color: #1e293b; margin: 16px 0 6px; }
h4 { font-size: 11pt; color: #64748b; margin: 12px 0 4px; }
p { margin: 6px 0; }
ul, ol { margin: 6px 0 8px; padding-left: 22px; }
li { margin: 2px 0; }
table { border-collapse: collapse; width: 100%; margin: 10px 0; font-size: 8.8pt; }
th { background: #4f46e5; color: #fff; text-align: left; padding: 5px 6px;
     font-weight: 600; }
td { border: 1px solid #cbd5e1; padding: 4px 6px; vertical-align: top; }
tr:nth-child(even) td { background: #f8fafc; }
tr { page-break-inside: avoid; }
code { font-family: Consolas, monospace; font-size: 8.8pt; color: #4f46e5;
       background: #eef2ff; padding: 1px 4px; border-radius: 3px; }
pre { background: #f1f5f9; border: 1px solid #e2e8f0; border-radius: 6px;
      padding: 10px; font-family: Consolas, monospace; font-size: 9pt;
      white-space: pre-wrap; page-break-inside: avoid; }
blockquote { border-left: 3px solid #cbd5e1; margin: 8px 0; padding: 4px 12px;
             color: #64748b; font-style: italic; }
hr { border: 0; border-top: 1px solid #e2e8f0; margin: 16px 0; }
.cover { text-align: center; padding-top: 120px; }
.cover h1 { border: 0; font-size: 30pt; }
.cover .sub { font-size: 14pt; color: #64748b; margin-top: 10px; }
.cover .brand { font-size: 11pt; color: #4f46e5; letter-spacing: 3px;
                text-transform: uppercase; margin-top: 220px; }
.cover .meta { font-size: 10pt; color: #64748b; margin-top: 8px; }
"""


def inline(text):
    """Escape HTML, then re-apply **bold** and `code`."""
    esc = html_mod.escape(text)
    esc = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", esc)
    esc = re.sub(r"`([^`]+)`", r"<code>\1</code>", esc)
    return esc


def md_to_html(md_path):
    with open(md_path, encoding="utf-8") as f:
        lines = f.read().splitlines()
    out = []
    i = 0
    in_code = False
    code_buf = []
    while i < len(lines):
        line = lines[i].rstrip()
        if line.strip().startswith("```"):
            if in_code:
                out.append("<pre>" + html_mod.escape("\n".join(code_buf)) + "</pre>")
                code_buf = []
            in_code = not in_code
            i += 1
            continue
        if in_code:
            code_buf.append(line)
            i += 1
            continue
        if re.fullmatch(r"-{3,}|\*{3,}|_{3,}", line.strip()):
            out.append("<hr>")
            i += 1
            continue
        if line.lstrip().startswith("|"):
            block = []
            while i < len(lines) and lines[i].lstrip().startswith("|"):
                cells = [c.strip() for c in lines[i].strip().strip("|").split("|")]
                if len(cells) == 1 or re.fullmatch(r":?-{2,}:?", cells[0]):
                    i += 1
                    continue
                block.append(cells)
                i += 1
            if len(block) > 1:
                rows = []
                for cells in block:
                    rows.append("<tr>" + "".join(
                        f"<{'th' if idx == 0 else 'td'}>{inline(c)}</{'th' if idx == 0 else 'td'}>"
                        for idx, c in enumerate(cells)) + "</tr>")
                out.append("<table>" + "".join(rows) + "</table>")
            continue
        m = re.match(r"^(#{1,4})\s+(.*)", line)
        if m:
            level = len(m.group(1))
            out.append(f"<h{level}>{inline(m.group(2).strip())}</h{level}>")
            i += 1
            continue
        m = re.match(r"^(\s*)[-*]\s+(.*)", line)
        if m:
            out.append(f"<li>{inline(m.group(2))}</li>")
            i += 1
            continue
        m = re.match(r"^(\s*)\d+\.\s+(.*)", line)
        if m:
            out.append(f"<li>{inline(m.group(2))}</li>")
            i += 1
            continue
        m = re.match(r"^>\s?(.*)", line)
        if m:
            out.append(f"<blockquote>{inline(m.group(1))}</blockquote>")
            i += 1
            continue
        if not line.strip():
            i += 1
            continue
        out.append(f"<p>{inline(line)}</p>")
        i += 1
    return "\n".join(out)


def wrap(title, body, cover=False):
    cover_html = ""
    if cover:
        logo_img = ""
        if LOGO_B64:
            logo_img = (
                '<img src="data:image/png;base64,' + LOGO_B64 +
                '" alt="Pixous Technologies" '
                'style="width:150px; max-width:45%; height:auto;"/>'
            )
        cover_html = f"""
<div class="cover">
  {logo_img}
  <div class="brand">Pixous Technologies</div>
  <h1>{title}</h1>
  <div class="sub">HR Management Portal — Client Submission</div>
  <div class="meta">Version 1.0 &middot; {__import__('datetime').date.today().isoformat()}</div>
</div>
<div style="page-break-after: always;"></div>"""
    return f"""<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>{title}</title>
<style>{CSS}</style></head><body>
{cover_html}
{body}
</body></html>"""


def api_to_html():
    with open(os.path.join(DOCS, "api-list.json"), encoding="utf-8") as f:
        data = json.load(f)
    rows = []
    for idx, e in enumerate(data["endpoints"], 1):
        rows.append(
            f"<tr><td>{idx}</td><td>{html_mod.escape(e['method'])}</td>"
            f"<td><code>{html_mod.escape(e['path'])}</code></td>"
            f"<td>{html_mod.escape(e['controller'].replace('.java', ''))}</td>"
            f"<td>{html_mod.escape(e['authorization'] or '—')}</td></tr>"
        )
    body = (
        f"<p><strong>{data['count']} endpoints</strong> · generated {data['generated']} "
        f"· base URL <code>{data['baseUrl']}</code></p>"
        f"<table><tr><th>#</th><th>Method</th><th>Path</th><th>Controller</th>"
        f"<th>Authorization</th></tr>{''.join(rows)}</table>"
    )
    return wrap("Pixous HR Portal — API List", body)


def main():
    os.makedirs(OUT, exist_ok=True)
    jobs = [
        ("REQUIREMENTS.md", "Pixous_HR_Requirements_v1.0.html",
         "Requirements Specification", True),
        ("UNIT-TESTING.md", "Pixous_HR_Unit_Testing_v1.0.html",
         "Unit Testing Specification", True),
        ("ROLE-WISE-TESTING.md", "Pixous_HR_RoleWise_Testing_v1.0.html",
         "Role-Wise Unit Testing Specification", True),
    ]
    for src, dst, title, cover in jobs:
        body = md_to_html(os.path.join(DOCS, src))
        with open(os.path.join(OUT, dst), "w", encoding="utf-8") as f:
            f.write(wrap(title, body, cover))
        print(f"Wrote {os.path.join(OUT, dst)}")
    with open(os.path.join(OUT, "Pixous_HR_API_List_v1.0.html"), "w", encoding="utf-8") as f:
        f.write(api_to_html())
    print(f"Wrote {os.path.join(OUT, 'Pixous_HR_API_List_v1.0.html')}")


if __name__ == "__main__":
    main()
