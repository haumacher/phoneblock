#!/usr/bin/env python3
"""Inject a fake SIP call into a dongle to exercise the incoming-call path.

The dongle accepts unauthenticated INVITEs on its SIP port (default UDP
15060) from any LAN host, so one crafted INVITE drives it through
check_invite_caller() -> blocklist/API lookup -> stats_record_call_checked()
and logs a call. Inspect the result on the web UI's "Letzte Anrufe" panel
or via GET http://<host>/api/calls.

Usage:
    scripts/fake-call.py <caller-number> [host] [sip-port]

    scripts/fake-call.py 030330755602
    scripts/fake-call.py 030330755602 192.168.178.68
    scripts/fake-call.py '*123' answerbot        # star-number spam test mode

Note: send only the bare number (no display name). A non-numeric display
name trips is_known_contact() and the call skips the blocklist/API check
(and is dropped entirely when "log known calls" is off).
"""
import socket, sys, time

caller = sys.argv[1] if len(sys.argv) > 1 else "030330755602"
host   = sys.argv[2] if len(sys.argv) > 2 else "answerbot"
port   = int(sys.argv[3]) if len(sys.argv) > 3 else 15060

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
    end = time.time() + secs
    while time.time() < end:
        try:
            data, _ = sock.recvfrom(4096)
        except socket.timeout:
            return
        print(f"  <= [{label}] {data.decode(errors='replace').splitlines()[0]}")

print("-> INVITE"); sock.send(msg("INVITE")); drain("after INVITE", 3.0)
print("-> CANCEL"); sock.send(msg("CANCEL")); drain("after CANCEL", 2.0)
sock.close()
print("done — check the call log on the dongle's web UI / GET /api/calls")
