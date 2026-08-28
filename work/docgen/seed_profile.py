"""
Sign in once and leave the session in a Chrome profile on disk.

Separated from the capture because the two need different things from Chrome:
this one needs a live debugger connection to write localStorage, and the
capture needs a clean `--screenshot` process per page. Doing both in one
long-lived connection meant every reply queued behind the app's own SockJS
traffic on the same socket, and the read timed out.

The profile is flushed by asking Chrome to exit properly. A terminate() skips
that flush, which is why an earlier version left the token behind in memory
and every screenshot came back showing the login page.
"""
import json, os, socket, subprocess, sys, time, urllib.request
import websocket

BASE = "https://pixoushrportal.pixous.info"
PROFILE = os.path.join(os.environ.get("TEMP", "/tmp"), "pixous-shot-profile")
PORT = 9345
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"

user, password = sys.argv[1], sys.argv[2]

req = urllib.request.Request(
    f"{BASE}/api/auth/login",
    data=json.dumps({"username": user, "password": password}).encode(),
    headers={"Content-Type": "application/json"})
tok = json.load(urllib.request.urlopen(req, timeout=45))["data"]["tokens"]

os.makedirs(PROFILE, exist_ok=True)
proc = subprocess.Popen(
    [CHROME, "--headless=new", "--disable-gpu", "--no-sandbox",
     f"--user-data-dir={PROFILE}", f"--remote-debugging-port={PORT}",
     "--remote-allow-origins=*", "about:blank"],
    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

for _ in range(60):
    try:
        socket.create_connection(("127.0.0.1", PORT), timeout=1).close(); break
    except OSError:
        time.sleep(0.5)
time.sleep(2)

targets = json.load(urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json", timeout=15))
page = next(t for t in targets if t.get("type") == "page")
ws = websocket.create_connection(page["webSocketDebuggerUrl"], timeout=60,
                                 suppress_origin=True)

def cmd(i, method, params=None):
    ws.send(json.dumps({"id": i, "method": method, "params": params or {}}))
    while True:
        m = json.loads(ws.recv())
        if m.get("id") == i:
            return m

# The login page is small and static, so nothing floods the socket here.
cmd(1, "Page.navigate", {"url": f"{BASE}/login"})
time.sleep(6)
out = cmd(2, "Runtime.evaluate", {
    "expression": (
        f"localStorage.setItem('hrp.accessToken', {json.dumps(tok['accessToken'])});"
        f"localStorage.setItem('hrp.refreshToken', {json.dumps(tok.get('refreshToken',''))});"
        "localStorage.getItem('hrp.accessToken') ? 'planted' : 'failed'"),
    "returnByValue": True})
print("seed:", out.get("result", {}).get("result", {}).get("value"))

# Ask Chrome to exit, so the profile is written out.
try:
    ws.send(json.dumps({"id": 99, "method": "Browser.close"}))
    time.sleep(2)
except Exception:
    pass
ws.close()
try:
    proc.wait(timeout=25)
except Exception:
    proc.terminate()
print("profile:", PROFILE)
