"""
Shared HTML/print styling for the PIXOUS testing PDFs.

The DOCX generators produce editable documents; these produce the printed
deliverable. Both draw on the same content, so the numbers cannot disagree.

Rendered through headless Chrome's --print-to-pdf, which is the approach this
repository already uses for the credentials document. That means real CSS
paged media: running headers and footers, page numbers, and control over where
a page may break -- none of which a DOCX gives reliably.

Design decisions worth stating, since they are deliberate rather than default:

* The palette is sampled from the logo itself rather than approximated: the
  wordmark's navy (#003050) and the deep green of the arc (#007050), on a very
  slightly warm ground -- pure white with pure black reads as an unstyled
  printout.
* Tables are the substance of these documents, so they get the most attention:
  a navy header band, hairline rules, zebra striping at 2% so it survives a
  black-and-white printer, and `break-inside: avoid` on rows so a row is never
  split across a page.
* Numerals are tabular everywhere a figure appears, so columns of measurements
  line up.
* Headings carry a rule rather than a colour block: a full-bleed colour bar per
  section prints as a solid ink stripe and looks cheap on paper.
"""
from __future__ import annotations

import base64
import datetime
import html
import os

_HERE = os.path.dirname(os.path.abspath(__file__))
_ROOT = os.path.dirname(os.path.dirname(_HERE))

# The full-resolution mark with a transparent background. work/pixous_logo.png
# has a grey rectangle baked into it, which printed as a grey box behind the
# logo on the cover; this one is the same mark as the web client's favicon,
# 2705x1494, and composites cleanly onto the page.
LOGO_PATH = os.path.join(_ROOT, 'web', 'public', 'pixous-favicon.png')
_FALLBACK_LOGO = os.path.join(os.path.dirname(_HERE), 'pixous_logo.png')


def logo_data_uri() -> str:
    """The logo inline, so a PDF never depends on a file path at render time."""
    for path in (LOGO_PATH, _FALLBACK_LOGO):
        if os.path.exists(path):
            with open(path, 'rb') as fh:
                return ('data:image/png;base64,'
                        + base64.b64encode(fh.read()).decode())
    return ''


def esc(text) -> str:
    return html.escape('' if text is None else str(text))


CSS = """
@page {
  size: A4;
  margin: 20mm 16mm 18mm 16mm;
  /* The running footer. Chrome renders @page margin boxes, so the page number
     comes from the paged-media engine rather than being faked per page. */
  @bottom-left {
    content: "PIXOUS Technologies  ·  Confidential";
    font-family: Inter, Calibri, sans-serif;
    font-size: 7.5pt;
    color: #7A8794;
  }
  @bottom-right {
    content: "Page " counter(page) " of " counter(pages);
    font-family: Inter, Calibri, sans-serif;
    font-size: 7.5pt;
    color: #7A8794;
  }
}
/* The cover carries its own furniture, so it takes no running footer. */
@page :first { @bottom-left { content: ""; } @bottom-right { content: ""; } }

:root {
  /* Sampled from the logo itself rather than chosen: the wordmark is
     #003050 and the arc runs from a deep green through to a light lime. */
  --navy:   #003050;
  --navy-2: #0A4468;
  --teal:   #007050;
  --ink:    #1D2530;
  --muted:  #5C6875;
  --rule:   #D9E0E7;
  --ground: #FBFCFD;
  --band:   #F2F6F9;
}

* { box-sizing: border-box; }

html { -webkit-print-color-adjust: exact; print-color-adjust: exact; }

body {
  margin: 0;
  font-family: Inter, Calibri, "Segoe UI", sans-serif;
  font-size: 9.4pt;
  line-height: 1.52;
  color: var(--ink);
  background: #fff;
  font-variant-numeric: tabular-nums;
}

/* ---------- cover ---------- */
.cover { height: 247mm; display: flex; flex-direction: column; }
.cover-top { text-align: center; padding-top: 14mm; }
.cover-logo { width: 46mm; height: auto; }
.cover-org {
  margin-top: 5mm; font-size: 8.2pt; letter-spacing: .26em;
  text-transform: uppercase; color: var(--muted); font-weight: 600;
}
.cover-mid { flex: 1; display: flex; flex-direction: column; justify-content: center; }
.cover-eyebrow {
  font-size: 8.4pt; letter-spacing: .2em; text-transform: uppercase;
  color: var(--teal); font-weight: 700; text-align: center;
}
.cover-title {
  margin: 3mm 0 0; font-size: 27pt; line-height: 1.12; font-weight: 800;
  color: var(--navy); text-align: center; text-wrap: balance;
}
.cover-rule {
  width: 26mm; height: 2.6pt; background: var(--teal);
  margin: 6mm auto 0; border-radius: 2pt;
}
.cover-lede {
  margin: 6mm auto 0; max-width: 118mm; text-align: center;
  font-size: 10pt; color: var(--muted); line-height: 1.6;
}
.cover-meta {
  margin-top: 12mm; border-top: 1px solid var(--rule);
  border-bottom: 1px solid var(--rule); padding: 4mm 0;
}
.cover-meta table { width: 100%; border-collapse: collapse; }
.cover-meta td { padding: 1.5mm 0; font-size: 8.8pt; vertical-align: top; }
.cover-meta td:first-child {
  width: 34mm; color: var(--muted); font-weight: 600;
  text-transform: uppercase; letter-spacing: .06em; font-size: 7.6pt;
  padding-top: 2.1mm;
}
.cover-foot {
  text-align: center; font-size: 7.6pt; color: var(--muted);
  padding-bottom: 4mm;
}

/* ---------- section headings ---------- */
h1 {
  font-size: 14pt; font-weight: 800; color: var(--navy);
  margin: 0 0 4mm; padding-bottom: 2mm;
  border-bottom: 2px solid var(--navy);
  break-after: avoid; break-inside: avoid;
}
h1 .num {
  display: inline-block; min-width: 9mm; color: var(--teal);
}
h2 {
  font-size: 10.6pt; font-weight: 700; color: var(--navy-2);
  margin: 6mm 0 2.5mm; break-after: avoid;
}
h2::before {
  content: ""; display: inline-block; width: 2.2mm; height: 2.2mm;
  background: var(--teal); margin-right: 2.2mm; vertical-align: middle;
  border-radius: .5mm;
}
p { margin: 0 0 3mm; }
.section { break-before: page; }
.section:first-of-type { break-before: auto; }

/* ---------- tables ---------- */
table.data {
  width: 100%; border-collapse: collapse; margin: 0 0 4mm;
  font-size: 8.2pt; break-inside: auto;
}
table.data thead th {
  background: var(--navy); color: #fff; font-weight: 600;
  text-align: left; padding: 2.1mm 2.2mm; font-size: 7.8pt;
  letter-spacing: .03em; border: none;
}
table.data thead { display: table-header-group; }
table.data tbody td {
  padding: 1.9mm 2.2mm; border-bottom: 1px solid var(--rule);
  vertical-align: top;
}
/* 2% so striping survives a black-and-white printer instead of vanishing. */
table.data tbody tr:nth-child(even) { background: #F7FAFC; }
table.data tbody tr { break-inside: avoid; }
table.data tbody tr.total td {
  font-weight: 700; background: var(--band); border-top: 1.6pt solid var(--navy);
}

/* ---------- callouts ---------- */
.note {
  border-left: 2.4pt solid var(--teal); background: var(--ground);
  padding: 2.4mm 3mm; margin: 0 0 4mm; font-size: 8.2pt;
  color: var(--muted); break-inside: avoid;
}
.finding {
  border: 1px solid var(--rule); border-left: 3pt solid #B4232A;
  padding: 3mm 3.4mm; margin: 0 0 4mm; break-inside: avoid;
  background: #FEFAFA;
}
.finding .ftitle {
  font-weight: 700; color: #8C1A20; font-size: 9.2pt; margin-bottom: 1.4mm;
}
.metric-row { display: flex; gap: 3mm; margin: 0 0 4mm; }
.metric {
  flex: 1; border: 1px solid var(--rule); border-radius: 1.6mm;
  padding: 3mm; text-align: center; background: var(--ground);
  break-inside: avoid;
}
.metric .v {
  font-size: 15pt; font-weight: 800; color: var(--navy); line-height: 1.1;
}
.metric .k {
  font-size: 7.4pt; color: var(--muted); text-transform: uppercase;
  letter-spacing: .07em; margin-top: 1mm;
}
ul { margin: 0 0 4mm; padding-left: 5mm; }
li { margin-bottom: 1.4mm; }
.pill {
  display: inline-block; padding: .4mm 1.8mm; border-radius: 3mm;
  font-size: 7.4pt; font-weight: 700;
}
.pass { background: #E3F4E8; color: #1B6B34; }
.fail { background: #FBE6E7; color: #8C1A20; }
.warn { background: #FDF2E1; color: #8A5A08; }
"""


def document(title: str, subtitle: str, lede: str, body: str,
             meta_extra: list[tuple[str, str]] | None = None) -> str:
    """A complete printable HTML document."""
    logo = logo_data_uri()
    logo_tag = (f'<img class="cover-logo" src="{logo}" alt="Pixous Technologies">'
                if logo else '<div class="cover-org">PIXOUS TECHNOLOGIES</div>')

    meta = [
        ('Document', title),
        ('Version', '1.0'),
        ('Date', datetime.date.today().strftime('%d %B %Y')),
        ('Application', 'PIXOUS HR Portal — backend, web portal and mobile app'),
        ('Environment', 'Production — pixoushrportal.pixous.info'),
        ('Prepared by', 'PIXOUS Technologies — Engineering'),
        ('Classification', 'Confidential — client deliverable'),
    ] + (meta_extra or [])

    meta_rows = '\n'.join(
        f'<tr><td>{esc(k)}</td><td>{esc(v)}</td></tr>' for k, v in meta)

    return f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<title>{esc(title)} — PIXOUS HR Portal</title>
<style>{CSS}</style></head>
<body>
<section class="cover">
  <div class="cover-top">
    {logo_tag}
    <div class="cover-org">Pixous Technologies · Coimbatore</div>
  </div>
  <div class="cover-mid">
    <div class="cover-eyebrow">{esc(subtitle)}</div>
    <h1 class="cover-title" style="border:none;padding:0">{esc(title)}</h1>
    <div class="cover-rule"></div>
    <p class="cover-lede">{esc(lede)}</p>
    <div class="cover-meta"><table>{meta_rows}</table></div>
  </div>
  <div class="cover-foot">
    This document contains confidential information about the PIXOUS HR Portal.
    Every figure quoted is measured, and the command that produced it is stated
    beside it.
  </div>
</section>
{body}
</body></html>"""


# ---------- small builders, so the four generators stay readable ----------

def h1(number: str, text: str) -> str:
    return f'<h1><span class="num">{esc(number)}</span>{esc(text)}</h1>'


def h2(text: str) -> str:
    return f'<h2>{esc(text)}</h2>'


def p(text: str) -> str:
    return f'<p>{esc(text)}</p>'


def note(text: str) -> str:
    return f'<div class="note">{esc(text)}</div>'


def finding(title: str, text: str) -> str:
    return (f'<div class="finding"><div class="ftitle">{esc(title)}</div>'
            f'{esc(text)}</div>')


def bullets(items: list[str]) -> str:
    lis = '\n'.join(f'<li>{esc(i)}</li>' for i in items)
    return f'<ul>{lis}</ul>'


def metrics(pairs: list[tuple[str, str]]) -> str:
    cells = '\n'.join(
        f'<div class="metric"><div class="v">{esc(v)}</div>'
        f'<div class="k">{esc(k)}</div></div>' for v, k in pairs)
    return f'<div class="metric-row">{cells}</div>'


def table(headers: list[str], rows: list[list], widths: list[str] | None = None,
          total_last: bool = False) -> str:
    cols = ''
    if widths:
        cols = '<colgroup>' + ''.join(
            f'<col style="width:{w}">' for w in widths) + '</colgroup>'
    th = ''.join(f'<th>{esc(h)}</th>' for h in headers)
    body = []
    for i, row in enumerate(rows):
        cls = ' class="total"' if (total_last and i == len(rows) - 1) else ''
        tds = ''.join(f'<td>{c if isinstance(c, Raw) else esc(c)}</td>'
                      for c in row)
        body.append(f'<tr{cls}>{tds}</tr>')
    return (f'<table class="data">{cols}<thead><tr>{th}</tr></thead>'
            f'<tbody>{"".join(body)}</tbody></table>')


class Raw(str):
    """Marks a cell whose content is already HTML (a status pill)."""


def pill(kind: str, text: str) -> Raw:
    return Raw(f'<span class="pill {kind}">{esc(text)}</span>')


def section(*parts: str) -> str:
    return '<section class="section">' + '\n'.join(parts) + '</section>'
