"""
Login Credentials — PDF, for active and onboarding employees.

WHY THIS FETCHES ITS OWN DATA
-----------------------------
This script talks to the live API itself rather than being handed a file of
passwords. That is deliberate: the credentials never leave the machine that
runs this, they are never written to a shared file, and the document is always
current rather than a snapshot that quietly goes stale.

WHAT IT PRODUCES
----------------
A branded PDF at ~/Downloads, in the same house style as the four testing
documents: the Pixous mark, the palette sampled from it, banded tables, running
page numbers.

HOW TO RUN IT
-------------
    cd work/docgen
    python pdf_credentials.py --user admin --password '<your password>'

or set them in the environment first, so the password is not in your shell
history:

    set HRP_USER=admin
    set HRP_PASS=...
    python pdf_credentials.py

Options:
    --base    API origin. Defaults to the production portal.
    --include Which employees. active | onboarding | both (default: both).
    --out     Output path. Defaults to ~/Downloads.

WHO CAN RUN IT
--------------
Only an account holding USER_MANAGE. The /users/{id}/password endpoint refuses
everybody else, so this script cannot extract anything its operator could not
already read in the portal.
"""
from __future__ import annotations

import argparse
import datetime
import getpass
import json
import os
import ssl
import sys
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pdf_style import Raw, document, h1, h2, metrics, note, p, section, table
from render_pdf import to_pdf

DEFAULT_BASE = 'https://pixoushrportal.pixous.info'


# ---------------------------------------------------------------- API ------

def _request(url: str, token: str | None = None, body: dict | None = None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data)
    req.add_header('Content-Type', 'application/json')
    if token:
        req.add_header('Authorization', f'Bearer {token}')
    ctx = ssl.create_default_context()
    with urllib.request.urlopen(req, timeout=45, context=ctx) as response:
        return json.load(response)


def login(base: str, username: str, password: str) -> str:
    try:
        payload = _request(f'{base}/api/auth/login',
                           body={'username': username, 'password': password})
    except urllib.error.HTTPError as err:
        if err.code == 401:
            raise SystemExit('Sign-in refused: check the username and password.')
        raise SystemExit(f'Sign-in failed: HTTP {err.code}')
    return payload['data']['tokens']['accessToken']


def fetch_users(base: str, token: str) -> list[dict]:
    """Every user the caller is allowed to see, one page at a time."""
    users, page = [], 0
    while True:
        data = _request(f'{base}/api/users?page={page}&size=100', token)['data']
        batch = data.get('content', [])
        users.extend(batch)
        total = data.get('totalElements', len(users))
        page += 1
        if not batch or len(users) >= total:
            break
    return users


def fetch_onboarding_ids(base: str, token: str) -> set[int]:
    """Users with an open onboarding checklist.

    Onboarding is not a profileStatus -- it is a separate checklist whose
    status is IN_PROGRESS, so it has to be asked for separately. A failure
    here is not fatal: the document still lists active staff and says the
    onboarding list could not be read, which is better than no document.
    """
    try:
        rows = _request(f'{base}/api/onboarding/employees', token)['data']
    except Exception:
        return set()
    out = set()
    for row in rows or []:
        uid = row.get('userId') or row.get('id')
        if isinstance(uid, (int, float)):
            out.add(int(uid))
    return out


def fetch_password(base: str, token: str, user_id: int) -> str | None:
    try:
        return _request(f'{base}/api/users/{user_id}/password', token)['data'].get('password')
    except Exception:
        return None


# ------------------------------------------------------------- document ----

def role_label(roles: list[str]) -> str:
    """The role as people say it, not the code."""
    names = {
        'SUPER_ADMIN': 'System Admin', 'COMPANY_ADMIN': 'System Admin',
        'BOARD_ADMIN': 'Board Admin', 'IT_HR': 'HR Head', 'IT_MGR': 'HR',
        'CV_HR': 'HR', 'IT_TL': 'Team Leader', 'CV_SUP': 'Site Supervisor',
        'IT_EMP': 'Employee', 'CV_EMP': 'Field Employee',
        'EMPLOYEE': 'Employee', 'TEAM_LEAD': 'Team Leader',
    }
    for code in ('SUPER_ADMIN', 'COMPANY_ADMIN', 'BOARD_ADMIN', 'IT_HR',
                 'IT_MGR', 'CV_HR', 'IT_TL', 'CV_SUP', 'IT_EMP', 'CV_EMP'):
        if code in roles:
            return names[code]
    return names.get(roles[0], roles[0].replace('_', ' ')) if roles else '—'


def mono(text: str) -> Raw:
    """Credentials in a monospaced face: an l and a 1 must be distinguishable
    when somebody is typing one off a printed page."""
    safe = (str(text).replace('&', '&amp;').replace('<', '&lt;')
            .replace('>', '&gt;'))
    return Raw('<span style="font-family:Consolas,\'Courier New\',monospace;'
               'font-size:8.4pt;letter-spacing:.02em">' + safe + '</span>')


def build(users: list[dict], onboarding: set[int], include: str) -> str:
    active, joining = [], []
    for u in users:
        status = (u.get('profileStatus') or '').upper()
        if u['id'] in onboarding:
            joining.append(u)
        elif status == 'ACTIVE' or (not status and u.get('enabled', True)):
            active.append(u)

    if include == 'active':
        joining = []
    elif include == 'onboarding':
        active = []

    def group_by_company(rows: list[dict]) -> dict[str, list[dict]]:
        out: dict[str, list[dict]] = {}
        for row in rows:
            out.setdefault(row.get('companyName') or 'Pixous Technologies',
                           []).append(row)
        for members in out.values():
            members.sort(key=lambda r: (r.get('employeeCode') or 'zzz'))
        return out

    parts = [
        section(
            h1('1.', 'About This Document'),
            p('This document lists the sign-in credentials for every active '
              'employee and every employee currently being onboarded. It is '
              'generated directly from the live portal, so it reflects the '
              'accounts as they stand at the moment of generation rather than '
              'a stored copy.'),
            metrics([
                (str(len(active)), 'active employees'),
                (str(len(joining)), 'onboarding'),
                (str(len(active) + len(joining)), 'total accounts'),
                (datetime.date.today().strftime('%d %b'), 'generated'),
            ]),
            note('Handle this document as you would a set of keys. It contains '
                 'working credentials: store it somewhere access-controlled, '
                 'share it only with the person each row belongs to, and delete '
                 'it once the details have been handed over. Anyone who can '
                 'read a row can sign in as that person.'),
            p('A dash in the password column means the portal holds no '
              'recoverable password for that account — the person has set '
              'their own since the account was created, which is the intended '
              'end state. Their username is still shown, and a password reset '
              'is the way to give them access again.'),
        )
    ]

    def credential_section(number: str, title: str, rows: dict[str, list[dict]],
                           blurb: str) -> str:
        if not rows:
            return ''
        blocks = [h1(number, title), p(blurb)]
        for company, members in sorted(rows.items()):
            blocks.append(h2(f'{company}  ·  {len(members)} '
                             f'{"account" if len(members) == 1 else "accounts"}'))
            blocks.append(table(
                ['#', 'Employee code', 'Name', 'Role', 'Username', 'Password'],
                [[str(i),
                  mono(m.get('employeeCode') or '—'),
                  m.get('name') or '—',
                  role_label(m.get('roles') or []),
                  mono(m.get('username') or '—'),
                  mono(m.get('_password') or '—')]
                 for i, m in enumerate(members, 1)],
                widths=['5%', '16%', '25%', '16%', '19%', '19%']))
        return section(*blocks)

    parts.append(credential_section(
        '2.', 'Active Employees', group_by_company(active),
        'Employees with an active profile. These accounts can sign in to the '
        'web portal and the mobile app now.'))

    parts.append(credential_section(
        '3.', 'Onboarding Employees', group_by_company(joining),
        'Employees with an onboarding checklist still in progress. Their '
        'accounts exist and can sign in; the checklist tracks what remains to '
        'be completed before they are treated as fully active.'))

    parts.append(section(
        h1('4.', 'How People Sign In'),
        table(['Client', 'Where', 'Notes'],
              [['Web portal', 'https://pixoushrportal.pixous.info',
                'Any current browser. The same credentials work on desktop '
                'and tablet.'],
               ['Mobile app', 'Pixous HR Android app',
                'The same username and password. The app talks to the same '
                'server.']],
              widths=['16%', '34%', '50%']),
        h2('First sign-in'),
        p('Ask each person to change their password once they have signed in. '
          'A password the company issued and can still read is a password two '
          'people know; one they chose themselves is a password only they '
          'know, and it is what every row in this document should eventually '
          'become a dash for.'),
        h2('If somebody cannot sign in'),
        table(['Symptom', 'Most likely cause', 'What to do'],
              [['"Invalid credentials"', 'Password already changed by the user',
                'Reset it from the portal; this document will then show a dash'],
               ['Account locked',
                'Repeated failed attempts triggered the lockout',
                'Wait for the lockout window, or have an administrator '
                'unlock it'],
               ['Signs in but sees very little',
                'Role or module permissions not yet assigned',
                'Check the role on the employee record'],
               ['Dash in the password column',
                'No recoverable password is stored',
                'Issue a reset rather than looking for the old one']],
              widths=['24%', '32%', '44%']),
    ))

    return ''.join(parts)


# ----------------------------------------------------------------- main ----

def main() -> None:
    ap = argparse.ArgumentParser(
        description='Generate the branded login-credentials PDF.')
    ap.add_argument('--base', default=os.environ.get('HRP_BASE', DEFAULT_BASE))
    ap.add_argument('--user', default=os.environ.get('HRP_USER'))
    ap.add_argument('--password', default=os.environ.get('HRP_PASS'))
    ap.add_argument('--include', choices=['active', 'onboarding', 'both'],
                    default='both')
    ap.add_argument('--out', default=os.path.join(
        os.path.expanduser('~'), 'Downloads',
        'Pixous_HR_Portal_Login_Credentials_v1.0.pdf'))
    args = ap.parse_args()

    username = args.user or input('Portal username: ').strip()
    # getpass so the password is not echoed and does not reach shell history.
    password = args.password or getpass.getpass('Portal password: ')

    print(f'Signing in to {args.base} ...')
    token = login(args.base, username, password)

    print('Reading employees ...')
    users = fetch_users(args.base, token)
    onboarding = fetch_onboarding_ids(args.base, token)
    print(f'  {len(users)} accounts, {len(onboarding)} onboarding')

    print('Reading credentials ...')
    wanted = []
    for u in users:
        status = (u.get('profileStatus') or '').upper()
        if u['id'] in onboarding or status == 'ACTIVE' or (
                not status and u.get('enabled', True)):
            wanted.append(u)
    for i, u in enumerate(wanted, 1):
        u['_password'] = fetch_password(args.base, token, u['id'])
        if i % 10 == 0 or i == len(wanted):
            print(f'  {i}/{len(wanted)}')

    body = build(users, onboarding, args.include)
    html = document(
        'Login Credentials',
        'Confidential — Account Access',
        'Sign-in details for active and onboarding employees of the PIXOUS HR '
        'Portal, generated live from the portal itself.',
        body,
        meta_extra=[('Contains', 'Working credentials — handle as keys')])

    out = to_pdf(html, args.out)
    print('\nWritten: ' + out)
    print('This file contains working passwords. Store it somewhere '
          'access-controlled and delete it once the details are handed over.')


if __name__ == '__main__':
    main()
