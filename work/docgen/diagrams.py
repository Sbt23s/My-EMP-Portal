"""
Workflow diagrams for the training guide, as inline SVG.

Inline rather than an image file or a charting library: these print at any
size without going soft, they take the document's own colours from the same
tokens the tables use, and there is nothing to install for somebody
regenerating the guide a year from now.

Every diagram here describes a chain that was read from the deployed code, not
one imagined for the picture. Where the code says a Team Leader's permission
goes to HR and to HR alone, the arrow goes to HR alone.
"""
from __future__ import annotations

NAVY = "#003050"
TEAL = "#007050"
INK = "#1D2530"
MUTED = "#5C6875"
RULE = "#D9E0E7"
BAND = "#F2F6F9"
AMBER = "#8A5A08"
AMBER_BG = "#FDF2E1"
GREEN_BG = "#E3F4E8"
GREEN = "#1B6B34"


def _box(x, y, w, h, label, sub=None, fill="#FFFFFF", stroke=NAVY, text=NAVY):
    """One node. Two lines when a subtitle is given, centred either way."""
    out = [
        f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="6" '
        f'fill="{fill}" stroke="{stroke}" stroke-width="1.4"/>'
    ]
    if sub:
        out.append(
            f'<text x="{x + w / 2}" y="{y + h / 2 - 4}" text-anchor="middle" '
            f'font-size="11.5" font-weight="700" fill="{text}">{label}</text>'
        )
        out.append(
            f'<text x="{x + w / 2}" y="{y + h / 2 + 11}" text-anchor="middle" '
            f'font-size="9" fill="{MUTED}">{sub}</text>'
        )
    else:
        out.append(
            f'<text x="{x + w / 2}" y="{y + h / 2 + 4}" text-anchor="middle" '
            f'font-size="11.5" font-weight="700" fill="{text}">{label}</text>'
        )
    return "".join(out)


def _arrow(x1, y1, x2, y2, label=None, colour=TEAL, dashed=False):
    dash = ' stroke-dasharray="5 4"' if dashed else ""
    out = [
        f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{colour}" '
        f'stroke-width="1.8" marker-end="url(#arw)"{dash}/>'
    ]
    if label:
        mx, my = (x1 + x2) / 2, (y1 + y2) / 2
        out.append(
            f'<text x="{mx}" y="{my - 7}" text-anchor="middle" font-size="9" '
            f'font-weight="600" fill="{colour}">{label}</text>'
        )
    return "".join(out)


def _svg(width, height, body):
    return (
        f'<svg viewBox="0 0 {width} {height}" width="100%" '
        f'style="max-width:{width}px;display:block;margin:2mm auto 4mm" '
        f'xmlns="http://www.w3.org/2000/svg" role="img">'
        '<defs><marker id="arw" viewBox="0 0 10 10" refX="9" refY="5" '
        'markerWidth="7" markerHeight="7" orient="auto-start-reverse">'
        f'<path d="M 0 0 L 10 5 L 0 10 z" fill="{TEAL}"/></marker></defs>'
        f"{body}</svg>"
    )


def approval_ladder() -> str:
    """Who a request goes to, by who raised it.

    The one diagram everything else refers back to: it is the same ladder for
    leave, permission and work from home, and the rung is decided by the
    applicant's own role rather than chosen from a list.
    """
    w, h = 760, 266
    body = [
        f'<text x="{w/2}" y="22" text-anchor="middle" font-size="12" '
        f'font-weight="800" fill="{NAVY}">One rung up, and only one</text>',

        # Row 1 — an employee's request goes to their own Team Leader.
        _box(30, 48, 150, 46, "Employee", "raises a request"),
        _arrow(180, 71, 268, 71, "goes to"),
        _box(268, 48, 150, 46, "Team Leader", "same team", fill=BAND),

        # Row 2 — a Team Leader's goes to HR, and to HR alone.
        _box(30, 110, 150, 46, "Team Leader", "raises a request"),
        _arrow(180, 133, 268, 133, "goes to"),
        _box(268, 110, 150, 46, "HR", fill=BAND),

        # Row 3 — HR's goes to the company head.
        _box(30, 172, 150, 46, "HR", "raises a request"),
        _arrow(180, 195, 268, 195, "goes to"),
        _box(268, 172, 150, 46, "CTO", "PIX-E100", fill=BAND),

        f'<text x="455" y="78" font-size="9.5" fill="{MUTED}">Only that Team '
        f'Leader may approve it.</text>',
        f'<text x="455" y="140" font-size="9.5" fill="{MUTED}">Not the CTO, not '
        f'an administrator — HR.</text>',
        f'<text x="455" y="202" font-size="9.5" fill="{MUTED}">The one person '
        f'above HR.</text>',

        f'<text x="{w/2}" y="248" text-anchor="middle" font-size="9" '
        f'fill="{MUTED}">The approver is decided by the server from the '
        f'applicant’s role. Nobody chooses their own approver.</text>',
    ]
    return _svg(w, h, "".join(body))


def decide_vs_view() -> str:
    """The distinction the whole system turns on."""
    w, h = 760, 200
    body = [
        f'<text x="{w/2}" y="22" text-anchor="middle" font-size="12" '
        f'font-weight="800" fill="{NAVY}">Seeing a request is not deciding it</text>',

        _box(40, 50, 300, 60, "The person it was sent to",
             "Approve · Reject · Comment",
             fill=GREEN_BG, stroke=GREEN, text=GREEN),
        _box(420, 50, 300, 60, "HR · CTO · System Admin",
             "View · Export · Report",
             fill=AMBER_BG, stroke=AMBER, text=AMBER),

        f'<text x="190" y="140" text-anchor="middle" font-size="9.5" '
        f'fill="{MUTED}">Exactly one person, named on the request</text>',
        f'<text x="570" y="140" text-anchor="middle" font-size="9.5" '
        f'fill="{MUTED}">Full visibility, no decision buttons</text>',

        f'<text x="{w/2}" y="180" text-anchor="middle" font-size="9" '
        f'fill="{MUTED}">Enforced on the server, so hiding a button is a '
        f'courtesy rather than the control.</text>',
    ]
    return _svg(w, h, "".join(body))


def request_lifecycle() -> str:
    """What happens to a request from raising to pay."""
    w, h = 780, 235
    y = 60
    body = [
        f'<text x="{w/2}" y="22" text-anchor="middle" font-size="12" '
        f'font-weight="800" fill="{NAVY}">A request, end to end</text>',

        _box(20, y, 128, 50, "Raised", "PENDING", fill=AMBER_BG,
             stroke=AMBER, text=AMBER),
        _arrow(148, y + 25, 196, y + 25),
        _box(196, y, 128, 50, "Notified", "approver told"),
        _arrow(324, y + 25, 372, y + 25),
        _box(372, y, 128, 50, "Reviewed", "files, comments"),
        _arrow(500, y + 25, 548, y + 25),
        _box(548, y, 128, 50, "Decided", "APPROVED / REJECTED",
             fill=GREEN_BG, stroke=GREEN, text=GREEN),

        _arrow(612, y + 50, 612, y + 92, colour=TEAL),
        _box(492, y + 92, 240, 46, "Applicant told at once",
             "the list updates itself", fill=BAND),

        f'<text x="{w/2}" y="{h - 14}" text-anchor="middle" font-size="9" '
        f'fill="{MUTED}">An approved work-from-home day is also written to '
        f'attendance, so it is counted present and paid.</text>',
    ]
    return _svg(w, h, "".join(body))


def wfh_to_pay() -> str:
    """Why an approved WFH day is a paid day."""
    w, h = 780, 160
    y = 52
    body = [
        f'<text x="{w/2}" y="22" text-anchor="middle" font-size="12" '
        f'font-weight="800" fill="{NAVY}">Work from home reaches the payslip</text>',
        _box(20, y, 150, 50, "WFH approved", fill=GREEN_BG, stroke=GREEN, text=GREEN),
        _arrow(170, y + 25, 218, y + 25),
        _box(218, y, 165, 50, "Attendance row", "status = WFH"),
        _arrow(383, y + 25, 431, y + 25),
        _box(431, y, 165, 50, "Counted present", "not absent"),
        _arrow(596, y + 25, 644, y + 25),
        _box(644, y, 116, 50, "Paid", fill=GREEN_BG, stroke=GREEN, text=GREEN),
        f'<text x="{w/2}" y="{h - 12}" text-anchor="middle" font-size="9" '
        f'fill="{MUTED}">Weekends and public holidays are skipped — they '
        f'were never working days.</text>',
    ]
    return _svg(w, h, "".join(body))


def duplicate_guard() -> str:
    """The rule that stops two records for one absence."""
    w, h = 780, 175
    body = [
        f'<text x="{w/2}" y="22" text-anchor="middle" font-size="12" '
        f'font-weight="800" fill="{NAVY}">One request per person per day</text>',

        _box(30, 52, 210, 48, "Sick Leave · 27 Aug",
             "already approved", fill=GREEN_BG, stroke=GREEN, text=GREEN),
        _box(30, 110, 210, 42, "Casual Leave · 27 Aug",
             "refused", fill="#FBE6E7", stroke="#8C1A20", text="#8C1A20"),

        f'<text x="270" y="72" font-size="10" fill="{INK}">One person, one day '
        f'— whatever the type.</text>',
        f'<text x="270" y="90" font-size="9.5" fill="{MUTED}">Two records for '
        f'one absence would deduct from two balances.</text>',
        f'<text x="270" y="118" font-size="9.5" fill="{MUTED}">Only APPROVED '
        f'and PENDING block. A rejected or cancelled</text>',
        f'<text x="270" y="133" font-size="9.5" fill="{MUTED}">request never '
        f'claimed the day, so it does not stand in the way.</text>',
    ]
    return _svg(w, h, "".join(body))
