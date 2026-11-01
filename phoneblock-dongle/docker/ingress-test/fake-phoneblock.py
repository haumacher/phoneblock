#!/usr/bin/env python3
"""A stand-in for phoneblock.net's device-code link endpoints (#621).

For trying the Linux service's link flow without the real server: the API
contract of docs/plans/2026-11-01-ingress-device-code-link-design.md, with
the state in memory and no login.

  POST <prefix>/api/link/start   {"appId", "label"} -> deviceCode, userCode, ...
  POST <prefix>/api/link/poll    {"deviceCode"} -> pending | slow_down |
                                 approved (+token) | denied | expired
  GET  <prefix>/link             code entry form (a ?code= is ignored, as on the
                                 real server); POST action=lookup shows the
                                 confirmation with [Verknüpfen] / [Ablehnen]
  GET  <prefix>/api/test         "ok" for a token this fake handed out
  GET  <prefix>/api/account      {"dialPrefix": "+49"}

--auto-approve N approves a link at its N-th poll, so the whole flow runs
without a browser. Tokens are fake ("fake-token-<n>"), valid only here.
"""

import argparse
import html
import json
import secrets
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

ALPHABET = "BCDFGHJKLMNPQRSTVWXZ"
LIFETIME_S = 600
INTERVAL_S = 5

lock = threading.Lock()
links = {}        # device code -> entry
tokens = set()
counter = [0]


def new_user_code():
    while True:
        raw = "".join(secrets.choice(ALPHABET) for _ in range(8))
        code = raw[:4] + "-" + raw[4:]
        if all(e["user_code"] != code for e in links.values()):
            return code


def normalize(code):
    return "".join(c for c in code.upper() if c in ALPHABET)


def find_by_user_code(code):
    wanted = normalize(code)
    for device_code, e in links.items():
        if normalize(e["user_code"]) == wanted:
            return device_code, e
    return None, None


def sweep():
    now = time.monotonic()
    for k in [k for k, e in links.items() if e["expires"] < now]:
        del links[k]


class Handler(BaseHTTPRequestHandler):
    server_version = "FakePhoneBlock/1"

    def log_message(self, fmt, *args):
        sys.stderr.write("fake-phoneblock: " + (fmt % args) + "\n")

    def send(self, status, body, ctype="application/json"):
        data = body.encode() if isinstance(body, str) else body
        self.send_response(status)
        self.send_header("Content-Type", ctype + "; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def json(self, status, obj):
        self.send(status, json.dumps(obj))

    def path_of(self):
        parts = urlsplit(self.path)
        prefix = self.server.prefix
        if not parts.path.startswith(prefix):
            return None, parts
        return parts.path[len(prefix):] or "/", parts

    def body(self):
        n = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(n) if n else b""

    def do_POST(self):
        path, _ = self.path_of()
        raw = self.body()
        if path in ("/api/link/start", "/api/link/poll"):
            try:
                req = json.loads(raw or b"{}")
            except ValueError:
                return self.json(400, {"message": "malformed JSON"})
            if path.endswith("start"):
                return self.start(req)
            return self.poll(req)
        if path == "/link":
            form = parse_qs(raw.decode())
            code = form.get("code", [""])[0]
            action = form.get("action", [""])[0]
            if action == "lookup":
                return self.page(code)
            return self.decide(code, action)
        self.json(404, {"message": "not found"})

    def do_GET(self):
        path, parts = self.path_of()
        if path == "/link":
            # The code must be typed in, a ?code= never leads to the confirmation.
            return self.page("")
        if path == "/api/test":
            auth = self.headers.get("Authorization", "")
            ok = auth.startswith("Bearer ") and auth[7:] in tokens
            return self.send(200 if ok else 401, "ok" if ok else "unauthorized", "text/plain")
        if path == "/api/account":
            return self.json(200, {"dialPrefix": "+49"})
        self.json(404, {"message": "not found"})

    def start(self, req):
        if req.get("appId") != "PhoneBlockDongle":
            return self.json(400, {"message": "unknown appId"})
        label = str(req.get("label") or "PhoneBlock-Dongle")[:64]
        with lock:
            sweep()
            device_code = secrets.token_urlsafe(32)
            user_code = new_user_code()
            links[device_code] = {
                "user_code": user_code, "label": label, "state": "pending",
                "expires": time.monotonic() + LIFETIME_S, "last_poll": 0.0,
                "polls": 0, "ua": self.headers.get("User-Agent", ""),
            }
        self.log_message("start: %s for %r", user_code, label)
        self.json(200, {
            "deviceCode": device_code,
            "userCode": user_code,
            "verifyUrl": self.server.public_url + "/link",
            "interval": INTERVAL_S,
            "expiresIn": LIFETIME_S,
        })

    def poll(self, req):
        device_code = str(req.get("deviceCode") or "")
        with lock:
            sweep()
            e = links.get(device_code)
            if not e:
                return self.json(200, {"status": "expired"})
            now = time.monotonic()
            too_fast = now - e["last_poll"] < INTERVAL_S - 0.5
            e["last_poll"] = now
            e["polls"] += 1
            if too_fast:
                return self.json(200, {"status": "slow_down"})
            if (e["state"] == "pending" and self.server.auto_approve
                    and e["polls"] >= self.server.auto_approve):
                e["state"] = "approved"
                self.log_message("auto-approved %s", e["user_code"])
            if e["state"] == "pending":
                return self.json(200, {"status": "pending"})
            # Single use: the entry goes with the answer.
            del links[device_code]
            if e["state"] == "denied":
                return self.json(200, {"status": "denied"})
            counter[0] += 1
            token = "fake-token-%d" % counter[0]
            tokens.add(token)
        self.log_message("token %s picked up for %r", token, e["label"])
        self.json(200, {"status": "approved", "token": token})

    def page(self, code, note=""):
        with lock:
            sweep()
            _, e = find_by_user_code(code) if code else (None, None)
        action = html.escape(self.server.prefix + "/link")
        if e and e["state"] == "pending":
            body = (
                "<h1>Gerät verknüpfen</h1>"
                "<p>Gerät: <b>%s</b></p>"
                "<p style='font:700 2rem monospace;letter-spacing:.15em'>%s</p>"
                "<p>Bestätige nur eine Verknüpfung, die du selbst gestartet hast.</p>"
                "<form method='post' action='%s'><input type='hidden' name='code' value='%s'>"
                "<button name='action' value='approve'>Verknüpfen</button> "
                "<button name='action' value='deny'>Ablehnen</button></form>"
            ) % (html.escape(e["label"]), html.escape(e["user_code"]), action,
                 html.escape(e["user_code"]))
        else:
            body = (
                "<h1>Code eingeben</h1>%s"
                "<form method='post' action='%s'><input name='code' autofocus> "
                "<button name='action' value='lookup'>Weiter</button></form>"
            ) % ("<p>Unbekannter oder abgelaufener Code.</p>" if code else "", action)
        self.send(200, "<!DOCTYPE html><meta charset='utf-8'><title>Fake PhoneBlock</title>"
                  "<body style='font-family:sans-serif;max-width:36rem;margin:2rem auto'>"
                  "<p><i>Fake-PhoneBlock (Ingress-Test)</i></p>" + note + body,
                  "text/html")

    def decide(self, code, action):
        with lock:
            _, e = find_by_user_code(code)
            if e and e["state"] == "pending" and action in ("approve", "deny"):
                e["state"] = "approved" if action == "approve" else "denied"
                done = e["state"]
            else:
                done = None
        if not done:
            return self.page(code)
        self.log_message("%s %s", done, code)
        self.send(200, "<!DOCTYPE html><meta charset='utf-8'><title>Fake PhoneBlock</title>"
                  "<p>%s Du kannst dieses Fenster schließen.</p>"
                  "<script>window.close()</script>"
                  % ("Verknüpft." if done == "approved" else "Abgelehnt."), "text/html")


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--port", type=int, default=8090)
    ap.add_argument("--prefix", default="/phoneblock",
                    help="context path, like the real server's /phoneblock")
    ap.add_argument("--public-url", default=None,
                    help="base URL the browser uses for the confirmation page "
                         "(default http://localhost:<port><prefix>)")
    ap.add_argument("--auto-approve", type=int, default=0, metavar="N",
                    help="approve a link at its N-th poll (0: wait for the page)")
    args = ap.parse_args()
    server = ThreadingHTTPServer(("0.0.0.0", args.port), Handler)
    server.prefix = args.prefix.rstrip("/")
    server.public_url = (args.public_url or
                         "http://localhost:%d%s" % (args.port, server.prefix)).rstrip("/")
    server.auto_approve = args.auto_approve
    print("fake-phoneblock: listening on :%d%s, confirmation page %s/link"
          % (args.port, server.prefix, server.public_url), flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
