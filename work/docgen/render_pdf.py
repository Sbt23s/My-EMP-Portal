"""
Render an HTML document to PDF with headless Chrome.

Chrome rather than a Python PDF library because these documents rely on CSS
paged media -- running footers, page counters, and break-inside rules that keep
a table row off a page boundary. A library that lays out its own pages cannot
honour those.

--print-to-pdf writes margins from the @page rule when
--no-pdf-header-footer is set; without it Chrome adds its own header with the
file:// URL in it, which looks like a mistake on a client deliverable.
"""
from __future__ import annotations

import os
import subprocess
import sys
import tempfile

CANDIDATES = [
    r'C:\Program Files\Google\Chrome\Application\chrome.exe',
    r'C:\Program Files (x86)\Google\Chrome\Application\chrome.exe',
    r'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe',
    r'C:\Program Files\Microsoft\Edge\Application\msedge.exe',
]


def find_browser() -> str:
    for path in CANDIDATES:
        if os.path.exists(path):
            return path
    raise SystemExit('No Chrome or Edge found for PDF rendering.')


def to_pdf(html: str, out_pdf: str, keep_html: str | None = None) -> str:
    os.makedirs(os.path.dirname(out_pdf), exist_ok=True)

    if keep_html:
        html_path = keep_html
        os.makedirs(os.path.dirname(html_path), exist_ok=True)
    else:
        html_path = os.path.join(tempfile.gettempdir(),
                                 os.path.basename(out_pdf) + '.html')
    with open(html_path, 'w', encoding='utf-8') as fh:
        fh.write(html)

    browser = find_browser()
    # A throwaway profile: without it Chrome may attach to a running instance
    # and return before the PDF is written.
    profile = tempfile.mkdtemp(prefix='pixous-pdf-')

    cmd = [
        browser,
        '--headless=new',
        '--disable-gpu',
        '--no-sandbox',
        '--no-first-run',
        '--no-pdf-header-footer',
        '--run-all-compositor-stages-before-draw',
        '--virtual-time-budget=8000',
        f'--user-data-dir={profile}',
        f'--print-to-pdf={out_pdf}',
        'file:///' + html_path.replace('\\', '/'),
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=240)

    if not os.path.exists(out_pdf) or os.path.getsize(out_pdf) < 2000:
        sys.stderr.write(result.stdout[-1500:] + '\n' + result.stderr[-1500:])
        raise SystemExit(f'PDF not produced: {out_pdf}')

    return out_pdf
