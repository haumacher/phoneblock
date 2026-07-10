#!/usr/bin/env python3
"""Inject a fake SIP call into a dongle to exercise the incoming-call path.

The dongle accepts unauthenticated INVITEs on its SIP port (default UDP
15060) from any LAN host, so one crafted INVITE drives it through
check_invite_caller() -> blocklist/API lookup -> stats_record_call_checked()
and logs a call. Inspect the result on the web UI's "Letzte Anrufe" panel
or via GET http://<host>/api/calls.

Usage:
    scripts/fake-call.py [--hang] <caller-number> [host] [sip-port]

    scripts/fake-call.py 030330755602
    scripts/fake-call.py 030330755602 192.168.178.68
    scripts/fake-call.py '*123' answerbot        # star-number spam test mode
    scripts/fake-call.py --hang 030330755602     # leave the call hanging

--hang: send only the INVITE and deliberately withhold the teardown (no
ACK, no CANCEL), leaving the dongle in its answered/ringing dialog. Use it
to reproduce a stuck call — e.g. answer a spam number with --hang, then
place a second normal call and check it is still screened (the dongle must
preempt the old dialog and handle the newest call).

Note: send only the bare number (no display name). A non-numeric display
name trips is_known_contact() and the call skips the blocklist/API check
(and is dropped entirely when "log known calls" is off).
"""
import socket, sys, time

args = sys.argv[1:]
hang = "--hang" in args
if hang:
    args.remove("--hang")
if not args or args[0] in ("-h", "--help"):
    print(__doc__)
    sys.exit(0 if args else 2)

caller = args[0]
host   = args[1] if len(args) > 1 else "answerbot"
port   = int(args[2]) if len(args) > 2 else 15060

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.settimeout(3.0)
sock.connect((host, port))                   # resolves host, picks source IP
my_ip, my_port = sock.getsockname()
print(f"local {my_ip}:{my_port} -> dongle {host}:{port}, caller={caller}")

tag = str(my_port)                           # unique per run
def msg(method):
    return (
        f"{method} sip:answerbot@{host} SIP/2.0\r\n"
        f"Via: SIP/2.0/UDP {my_ip}:{my_port};branch=z9hG4bK-fakecall-{tag}\r\n"
        f"Max-Forwards: 70\r\n"
        f"From: <sip:{caller}@{my_ip}>;tag=fakecall{tag}\r\n"
        f"To: <sip:answerbot@{host}>\r\n"
        f"Call-ID: fakecall-{tag}@{my_ip}\r\n"
        f"CSeq: 1 {method}\r\n"
        f"Contact: <sip:{caller}@{my_ip}:{my_port}>\r\n"
        f"Content-Length: 0\r\n\r\n"
    ).encode()

def drain(label, secs):
    """Print responses for `secs`; return the last final status code (>=200)."""
    final = None
    end = time.time() + secs
    while time.time() < end:
        try:
            data, _ = sock.recvfrom(4096)
        except socket.timeout:
            break
        line = data.decode(errors="replace").splitlines()[0]
        print(f"  <= [{label}] {line}")
        parts = line.split()
        if len(parts) >= 2 and parts[0] == "SIP/2.0" and parts[1].isdigit():
            code = int(parts[1])
            if code >= 200:
                final = code
    return final

# Allow >3 s so the non-spam decline (480 after SIP_DECLINE_DELAY) is seen.
print("-> INVITE"); sock.send(msg("INVITE")); final = drain("after INVITE", 3.5)
if hang:
    print("-> (--hang) leaving dialog open: no ACK/CANCEL sent")
else:
    # Tear the dialog down so the dongle's single slot is freed: ACK any final
    # response (2xx spam answer or 4xx decline), else CANCEL a still-ringing call.
    if final is not None:
        print(f"-> ACK (final {final})"); sock.send(msg("ACK")); drain("after ACK", 1.5)
    else:
        print("-> CANCEL (still ringing)"); sock.send(msg("CANCEL")); drain("after CANCEL", 1.5)
sock.close()
print("done — check the call log on the dongle's web UI / GET /api/calls")
