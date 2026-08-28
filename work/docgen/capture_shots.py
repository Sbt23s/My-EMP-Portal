"""
Capture real screenshots of the live portal, signed in, for the training guide.

Screenshots rather than mock-ups: a training document showing something the
user will never see is worse than one with no pictures.

ONE BROWSER, NOT TWENTY-TWO

An earlier version planted the session token and then launched a fresh
`--screenshot` process per page. Every one came back showing the login screen:
localStorage is flushed to disk lazily, and a separate process does not
inherit what the first held in memory, so each capture arrived before the
token did.

This keeps a single headless Chrome alive for the whole run and drives it over
the DevTools protocol -- sign in once, then navigate and photograph in the
same session, which is also what a person actually does.

Run:
    python capture_shots.py --user admin --password '<password>'
"""
from __future__ import annotations

import argparse
import base64
import getpass
import json
import os
import socket
import subprocess
import time
import urllib.request

BASE = "https://pixoushrportal.pixous.info"
HERE = os.path.dirname(os.path.abspath(__file__))
SHOTS = os.path.join(HERE, "shots")
PROFILE = os.path.join(os.environ.get("TEMP", "/tmp"), "pixous-shot-profile")
PORT = 9222

CHROME_CANDIDATES = [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
]

TOKEN_KEY = "hrp.accessToken"
REFRESH_KEY = "hrp.refreshToken"

PAGES = [
    ("dashboard", "/"),
    ("attendance", "/attendance"),
    ("leave", "/leave"),
    ("permission", "/leave/permissions"),
    ("wfh", "/leave/wfh"),
    ("approvals", "/leave/approvals"),
    ("payslips", "/payslips"),
    ("work-reports", "/work-reports"),
    ("tasks", "/tasks"),
    ("claims", "/ta-expenses"),
    ("assets", "/assets"),
    ("supports", "/helpdesk"),
    ("complaints", "/complaints"),
    ("chat", "/chat"),
    ("communities", "/communities"),
    ("calendar", "/calendar"),
    ("teams", "/teams"),
    ("employees", "/employees"),
    ("team-attendance", "/team-attendance"),
    ("reports", "/reports"),
    ("audit", "/audit"),
    ("profile", "/profile"),
]


def chrome() -> str:
    for path in CHROME_CANDIDATES:
        if os.path.exists(path):
            return path
    raise SystemExit("No Chrome or Edge found.")


def sign_in(username: str, password: str) -> tuple[str, str]:
    req = urllib.request.Request(
        f"{BASE}/api/auth/login",
        data=json.dumps({"username": username, "password": password}).encode(),
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=45) as r:
        payload = json.load(r)
    t = payload["data"]["tokens"]
    return t["accessToken"], t.get("refreshToken", "")


class Session:
    """One long-lived headless Chrome, driven over DevTools."""

    def __init__(self, browser: str, width: int, height: int):
        self.proc = subprocess.Popen(
            [browser, "--headless=new", "--disable-gpu", "--no-sandbox",
             "--hide-scrollbars", f"--window-size={width},{height}",
             f"--user-data-dir={PROFILE}", f"--remote-debugging-port={PORT}",
             "--remote-allow-origins=*", "about:blank"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

        for _ in range(80):
            try:
                with socket.create_connection(("127.0.0.1", PORT), timeout=1):
                    break
            except OSError:
                time.sleep(0.5)
        else:
            self.close()
            raise SystemExit("Chrome did not open its debugging port.")

        import websocket

        time.sleep(2)
        with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json", timeout=20) as r:
            targets = json.load(r)
        page = next((t for t in targets if t.get("type") == "page"), None)
        if not page:
            self.close()
            raise SystemExit("No page target to attach to.")

        # No Origin header: websocket-client sends one, Chrome then treats the
        # connection as cross-origin and refuses it. A debugger client is not a
        # browser page, so the header is meaningless here.
        self.ws = websocket.create_connection(
            page["webSocketDebuggerUrl"], timeout=240, suppress_origin=True)
        self._id = 0

    def send(self, method: str, params: dict | None = None) -> dict:
        self._id += 1
        self.ws.send(json.dumps({"id": self._id, "method": method,
                                 "params": params or {}}))
        while True:
            msg = json.loads(self.ws.recv())
            if msg.get("id") == self._id:
                return msg

    def evaluate(self, expression: str):
        out = self.send("Runtime.evaluate",
                        {"expression": expression, "returnByValue": True})
        return out.get("result", {}).get("result", {}).get("value")

    def goto(self, url: str, settle: float = 4.0) -> None:
        self.send("Page.navigate", {"url": url})
        # A fixed settle rather than the load event: this app fetches after
        # first paint, and load fires well before the tables these pictures
        # are meant to show.
        time.sleep(settle)

    def shot(self, path: str) -> bool:
        """JPEG, not PNG: a full-page PNG runs to several megabytes over the
        debugger socket and the read times out mid-frame. At quality 90 the
        difference is invisible in print and the transfer is a fraction."""
        out = self.send("Page.captureScreenshot",
                        {"format": "jpeg", "quality": 90})
        data = out.get("result", {}).get("data")
        if not data:
            return False
        with open(path, "wb") as fh:
            fh.write(base64.b64decode(data))
        return os.path.getsize(path) > 5000

    def close(self) -> None:
        try:
            # A clean exit, so localStorage is flushed to the profile. A
            # terminate() skips that, which is why the token used to vanish.
            self.send("Browser.close")
        except Exception:
            pass
        try:
            self.ws.close()
        except Exception:
            pass
        try:
            self.proc.terminate()
            self.proc.wait(timeout=20)
        except Exception:
            pass


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--user", default=os.environ.get("HRP_USER"))
    ap.add_argument("--password", default=os.environ.get("HRP_PASS"))
    ap.add_argument("--width", type=int, default=1500)
    ap.add_argument("--height", type=int, default=1000)
    ap.add_argument("--settle", type=float, default=4.0)
    args = ap.parse_args()

    username = args.user or input("Portal username: ").strip()
    password = args.password or getpass.getpass("Portal password: ")

    os.makedirs(SHOTS, exist_ok=True)
    os.makedirs(PROFILE, exist_ok=True)

    print("signing in...")
    access, refresh = sign_in(username, password)

    print("opening browser...")
    s = Session(chrome(), args.width, args.height)
    try:
        # localStorage is per-origin, so the token has to be written while a
        # page from the portal is open -- not on about:blank.
        s.goto(f"{BASE}/login", settle=args.settle)
        s.evaluate(
            f"localStorage.setItem({json.dumps(TOKEN_KEY)}, {json.dumps(access)});"
            f"localStorage.setItem({json.dumps(REFRESH_KEY)}, {json.dumps(refresh)});"
            "'planted'")

        s.goto(f"{BASE}/", settle=args.settle + 3)
        text = s.evaluate("document.body.innerText.slice(0,120)") or ""
        if "Sign in" in text:
            raise SystemExit("Still on the sign-in page - the token was not accepted.")
        print("  signed in")

        captured, failed = [], []
        for name, path in PAGES:
            s.goto(f"{BASE}{path}", settle=args.settle)
            ok = s.shot(os.path.join(SHOTS, f"{name}.png"))
            (captured if ok else failed).append(name)
            print(f"  {name:18} {'ok' if ok else 'FAILED'}")

        print(f"\ncaptured {len(captured)} / {len(PAGES)} into {SHOTS}")
        if failed:
            print("failed:", ", ".join(failed))
    finally:
        s.close()


if __name__ == "__main__":
    main()
