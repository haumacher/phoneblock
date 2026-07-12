#!/usr/bin/env python3
"""Zero-dependency MCP server exposing the PhoneBlock diagnostics admin API.

Wraps the REST endpoints under ``<base>/admin/diag/*`` (see the OpenAPI spec at
``<base>/admin/openapi.json``) as MCP tools so an agent can inspect the log
analysis and propose/audit scrub rules without hand-rolling curl.

Config (all optional, via environment):
  PHONEBLOCK_API          base API URL, ending in ``/api``
                          (default: https://phoneblock.net/phoneblock/api = prod)
  PHONEBLOCK_TOKEN_SERVER settings.xml <server> id to read the token from
                          (default: ``phoneblock-admin``); lets prod and test
                          use separate tokens.
  PHONEBLOCK_ADMIN_TOKEN  the bearer token; if unset, it is read from
                          ~/.m2/settings.xml server id PHONEBLOCK_TOKEN_SERVER
                          (``<password>`` or ``<passphrase>``) — the same
                          secret source the translate plugin uses.

The token is NEVER read from or written to the repo. Talks newline-delimited
JSON-RPC 2.0 over stdio (the MCP stdio transport). Protocol notes go to stderr;
stdout carries only protocol messages.
"""

import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

SERVER_NAME = "phoneblock-diag"
SERVER_VERSION = "1.0.0"
DEFAULT_API = "https://phoneblock.net/phoneblock/api"


def log(*args):
    print(*args, file=sys.stderr, flush=True)


# --------------------------------------------------------------------------- #
# Config / secret loading
# --------------------------------------------------------------------------- #

def base_url():
    return os.environ.get("PHONEBLOCK_API", DEFAULT_API).rstrip("/")


def load_token():
    tok = os.environ.get("PHONEBLOCK_ADMIN_TOKEN")
    if tok and tok.strip():
        return tok.strip()
    # Fall back to a Maven settings.xml server (same secret store as the translate
    # plugin). The env picks which one, so prod and test use separate tokens.
    server = os.environ.get("PHONEBLOCK_TOKEN_SERVER", "phoneblock-admin")
    path = os.path.expanduser("~/.m2/settings.xml")
    try:
        xml = open(path, encoding="utf-8").read()
    except OSError:
        return None
    block = re.search(
        r"<server>\s*<id>\s*" + re.escape(server) + r"\s*</id>(.*?)</server>", xml, re.S)
    if not block:
        return None
    for tag in ("password", "passphrase"):
        m = re.search(r"<%s>(.*?)</%s>" % (tag, tag), block.group(1), re.S)
        if m:
            return m.group(1).strip()
    return None


# --------------------------------------------------------------------------- #
# HTTP
# --------------------------------------------------------------------------- #

def api(method, path, query=None, body=None):
    """Call ``<base>/admin/diag<path>`` and return parsed JSON (or text)."""
    token = load_token()
    if not token:
        raise RuntimeError(
            "No admin token. Set PHONEBLOCK_ADMIN_TOKEN or add a "
            "<server><id>phoneblock-admin</id>… to ~/.m2/settings.xml.")
    url = base_url() + "/admin/diag" + path
    if query:
        clean = {k: v for k, v in query.items() if v is not None}
        if clean:
            url += "?" + urllib.parse.urlencode(clean)
    data = None
    headers = {"Authorization": "Bearer " + token, "Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", "replace")
        raise RuntimeError("HTTP %s %s: %s" % (e.code, url, detail.strip()))
    except urllib.error.URLError as e:
        raise RuntimeError("Cannot reach %s: %s" % (url, e.reason))
    raw = raw.strip()
    if not raw:
        return {"ok": True}
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return raw


# --------------------------------------------------------------------------- #
# Tools
# --------------------------------------------------------------------------- #

def t_ingest_status(_):
    return api("GET", "/ingest/status")


def t_list_signatures(a):
    rows = api("GET", "/signatures",
               query={"limit": a.get("limit", 100), "source": a.get("source")})
    # Rank by volume and project to the fields the tuning loop actually reads, so
    # the ranked view is native — no reason to curl + sort by hand.
    if isinstance(rows, list):
        rows.sort(key=lambda s: -(s.get("totalEvents") or 0))
        rows = [{"totalEvents": s.get("totalEvents"), "source": s.get("source"),
                 "tag": s.get("tag"), "signature": s.get("signature"),
                 "sigId": s.get("sigId")} for s in rows]
    return rows


def t_get_signature(a):
    return api("GET", "/signatures/" + urllib.parse.quote(str(a["sigId"])))


def t_origin_timeline(a):
    return api("GET", "/origins/%s/%s/timeline" % (
        urllib.parse.quote(str(a["source"])),
        urllib.parse.quote(str(a["originId"]))))


def t_audit_scrub(a):
    body = {"limit": a.get("limit", 500)}
    if a.get("source"):
        body["source"] = a["source"]
    if a.get("candidatePattern"):
        body["candidatePattern"] = a["candidatePattern"]
    return api("POST", "/scrub/audit", body=body)


def t_list_scrub_rules(a):
    return api("GET", "/scrub", query={"state": a.get("state")})


def t_create_scrub_rule(a):
    body = {
        "name": a.get("name", ""),
        "pattern": a["pattern"],
        "replacement": a.get("replacement", ""),
        "appliesTo": a.get("appliesTo", "BOTH"),
        "state": a.get("state", "DRAFT"),
    }
    if a.get("source"):
        body["source"] = a["source"]
    return api("POST", "/scrub", body=body)


def t_set_scrub_state(a):
    return api("POST", "/scrub/%s/state" % int(a["id"]),
               body={"state": a["state"]})


def t_list_rules(_):
    return api("GET", "/rules")


def t_list_notifications(a):
    return api("GET", "/notifications", query={"state": a.get("state")})


def t_create_rule(a):
    return api("POST", "/rules", body={k: v for k, v in a.items() if v is not None})


def t_update_rule(a):
    body = {k: v for k, v in a.items() if k != "id" and v is not None}
    return api("POST", "/rules/%d" % int(a["id"]), body=body)


def t_set_rule_state(a):
    return api("POST", "/rules/%d/state" % int(a["id"]), body={"state": a["state"]})


TOOLS = [
    {
        "name": "ingest_status",
        "description": "Log-ingestor health: stream offset, lag, and running "
                       "counts of signatures / origin-signatures / events / samples.",
        "inputSchema": {"type": "object", "properties": {}},
        "handler": t_ingest_status,
    },
    {
        "name": "list_signatures",
        "description": "List aggregated log signatures (normalized+scrubbed "
                       "grouping keys) with event counts. Rank by totalEvents to "
                       "spot noise and fragmentation.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "limit": {"type": "integer", "default": 100},
                "source": {"type": "string",
                           "description": "Filter, e.g. SERVER or DONGLE."},
            },
        },
        "handler": t_list_signatures,
    },
    {
        "name": "get_signature",
        "description": "One signature with its retained (scrubbed) sample lines. "
                       "Use the raw samples to design an anchored scrub rule.",
        "inputSchema": {
            "type": "object",
            "properties": {"sigId": {"type": "string"}},
            "required": ["sigId"],
        },
        "handler": t_get_signature,
    },
    {
        "name": "origin_timeline",
        "description": "Event timeline for one origin (e.g. a specific device or "
                       "answerbot) within a source.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "source": {"type": "string"},
                "originId": {"type": "string"},
            },
            "required": ["source", "originId"],
        },
        "handler": t_origin_timeline,
    },
    {
        "name": "audit_scrub",
        "description": "Probe retained samples with a candidate regex (no side "
                       "effects): reports which samples it would newly mask. Use "
                       "to validate a proposed scrub pattern against live data and "
                       "confirm it hits only the intended family. Without a "
                       "pattern, runs built-in PII-shape probes.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "candidatePattern": {"type": "string"},
                "source": {"type": "string"},
                "limit": {"type": "integer", "default": 500},
            },
        },
        "handler": t_audit_scrub,
    },
    {
        "name": "list_scrub_rules",
        "description": "List DIAG_SCRUB_RULE rows (the hot-editable anonymizer "
                       "layer on top of the built-in scrubber).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "state": {"type": "string",
                          "description": "Filter: DRAFT, LIVE or DISABLED."},
            },
        },
        "handler": t_list_scrub_rules,
    },
    {
        "name": "create_scrub_rule",
        "description": "Create a scrub rule. Defaults to state=DRAFT (stored but "
                       "not applied, so it can be audited first). state=LIVE also "
                       "needs an admin-capable token. Replacement supports $1 "
                       "group refs; anchor the pattern on a stable prefix.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "name": {"type": "string"},
                "pattern": {"type": "string"},
                "replacement": {"type": "string"},
                "appliesTo": {"type": "string", "enum": ["SIGNATURE", "SAMPLE", "BOTH"],
                              "default": "BOTH"},
                "source": {"type": "string"},
                "state": {"type": "string", "enum": ["DRAFT", "LIVE", "DISABLED"],
                          "default": "DRAFT"},
            },
            "required": ["pattern"],
        },
        "handler": t_create_scrub_rule,
    },
    {
        "name": "set_scrub_state",
        "description": "Promote/retire a scrub rule (DRAFT|LIVE|DISABLED). "
                       "Admin-gated.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "id": {"type": "integer"},
                "state": {"type": "string", "enum": ["DRAFT", "LIVE", "DISABLED"]},
            },
            "required": ["id", "state"],
        },
        "handler": t_set_scrub_state,
    },
    {
        "name": "list_rules",
        "description": "List detection rules (the notification/matcher rules, e.g. "
                       "the dongle silence detector) — distinct from scrub rules.",
        "inputSchema": {"type": "object", "properties": {}},
        "handler": t_list_rules,
    },
    {
        "name": "list_notifications",
        "description": "List help-mail notifications with their SHADOW/LIVE state.",
        "inputSchema": {
            "type": "object",
            "properties": {"state": {"type": "string"}},
        },
        "handler": t_list_notifications,
    },
    {
        "name": "create_rule",
        "description": "Create a detection rule (lands SHADOW/DRAFT; promote via "
                       "set_rule_state). matchRegex is tested against the signature "
                       "text; actor NONE|USER|DEV.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "name": {"type": "string"},
                "source": {"type": "string"},
                "matchTag": {"type": "string"},
                "matchRegex": {"type": "string"},
                "category": {"type": "string"},
                "actor": {"type": "string", "enum": ["NONE", "USER", "DEV"]},
                "minDistinctDays": {"type": "integer"},
                "minEvents": {"type": "integer"},
                "templateKey": {"type": "string"},
                "notes": {"type": "string"},
                "state": {"type": "string", "enum": ["DRAFT", "SHADOW"]},
            },
            "required": ["matchRegex"],
        },
        "handler": t_create_rule,
    },
    {
        "name": "update_rule",
        "description": "Edit a detection rule's definition (patch: only the given "
                       "fields change; state is changed via set_rule_state). Use to "
                       "retune matchRegex/thresholds without a redeploy. Editing a "
                       "LIVE rule needs the admin capability.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "id": {"type": "integer"},
                "name": {"type": "string"},
                "source": {"type": "string"},
                "matchTag": {"type": "string"},
                "matchRegex": {"type": "string"},
                "category": {"type": "string"},
                "actor": {"type": "string", "enum": ["NONE", "USER", "DEV"]},
                "minDistinctDays": {"type": "integer"},
                "minEvents": {"type": "integer"},
                "templateKey": {"type": "string"},
                "notes": {"type": "string"},
            },
            "required": ["id"],
        },
        "handler": t_update_rule,
    },
    {
        "name": "set_rule_state",
        "description": "Promote/retire a detection rule "
                       "(DRAFT|SHADOW|LIVE|DISABLED). LIVE requires admin.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "id": {"type": "integer"},
                "state": {"type": "string", "enum": ["DRAFT", "SHADOW", "LIVE", "DISABLED"]},
            },
            "required": ["id", "state"],
        },
        "handler": t_set_rule_state,
    },
]

TOOL_BY_NAME = {t["name"]: t for t in TOOLS}


# --------------------------------------------------------------------------- #
# JSON-RPC / MCP plumbing
# --------------------------------------------------------------------------- #

def make_result(req_id, result):
    return {"jsonrpc": "2.0", "id": req_id, "result": result}


def make_error(req_id, code, message):
    return {"jsonrpc": "2.0", "id": req_id, "error": {"code": code, "message": message}}


def handle(msg):
    """Return a response dict, or None for notifications."""
    method = msg.get("method")
    req_id = msg.get("id")
    if method == "initialize":
        proto = msg.get("params", {}).get("protocolVersion", "2024-11-05")
        return make_result(req_id, {
            "protocolVersion": proto,
            "capabilities": {"tools": {}},
            "serverInfo": {"name": SERVER_NAME, "version": SERVER_VERSION},
        })
    if method in ("notifications/initialized", "initialized"):
        return None
    if method == "ping":
        return make_result(req_id, {})
    if method == "tools/list":
        listed = [{"name": t["name"], "description": t["description"],
                   "inputSchema": t["inputSchema"]} for t in TOOLS]
        return make_result(req_id, {"tools": listed})
    if method == "tools/call":
        params = msg.get("params", {})
        name = params.get("name")
        args = params.get("arguments") or {}
        tool = TOOL_BY_NAME.get(name)
        if not tool:
            return make_error(req_id, -32602, "Unknown tool: %s" % name)
        try:
            out = tool["handler"](args)
            text = out if isinstance(out, str) else json.dumps(out, indent=1, ensure_ascii=False)
            if len(text) > 40000:
                text = text[:40000] + "\n… (truncated; narrow with limit/source)"
            return make_result(req_id, {"content": [{"type": "text", "text": text}]})
        except Exception as e:  # noqa: BLE001 — surface any failure to the caller
            return make_result(req_id, {
                "content": [{"type": "text", "text": "Error: %s" % e}],
                "isError": True,
            })
    if req_id is not None:
        return make_error(req_id, -32601, "Method not found: %s" % method)
    return None


def main():
    log("[%s] up — API=%s token=%s" % (
        SERVER_NAME, base_url(), "yes" if load_token() else "MISSING"))
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            msg = json.loads(line)
        except json.JSONDecodeError as e:
            log("bad json:", e)
            continue
        try:
            resp = handle(msg)
        except Exception as e:  # noqa: BLE001
            resp = make_error(msg.get("id"), -32603, "Internal error: %s" % e)
        if resp is not None:
            sys.stdout.write(json.dumps(resp) + "\n")
            sys.stdout.flush()


if __name__ == "__main__":
    main()
