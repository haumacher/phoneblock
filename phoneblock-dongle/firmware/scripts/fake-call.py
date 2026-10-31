#!/usr/bin/env python3
"""Inject a fake SIP call into a dongle to exercise the incoming-call path.

The dongle accepts unauthenticated INVITEs on its SIP port (default UDP
15060) from any LAN host, so one crafted INVITE drives it through
check_invite_caller() -> blocklist/API lookup -> stats_record_call_checked()
and logs a call. Inspect the result on the web UI's "Letzte Anrufe" panel
or via GET http://<host>/api/calls.

Usage:
    scripts/fake-call.py [--hang | --audio] <caller-number> [host] [sip-port]

    scripts/fake-call.py 030330755602
    scripts/fake-call.py 030330755602 192.168.178.68
    scripts/fake-call.py '*123' answerbot        # star-number spam test mode
    scripts/fake-call.py --hang 030330755602     # leave the call hanging
    scripts/fake-call.py --audio 030330755602    # receive the announcement
    scripts/fake-call.py --audio --bye-after 3 '*123'   # caller hangs up early

--hang: send only the INVITE and deliberately withhold the teardown (no
ACK, no CANCEL), leaving the dongle in its answered/ringing dialog. Use it
to reproduce a stuck call — e.g. answer a spam number with --hang, then
place a second normal call and check it is still screened (the dongle must
preempt the old dialog and handle the newest call).

--audio: offer a PCMA audio stream in the INVITE (SDP), so a spam verdict
plays the announcement to this host. After the ACK the script receives the
RTP stream until the dongle hangs up (answered with 200) or the stream stays
silent for 5 s (then it sends BYE itself), and prints the packet count and
duration. Use it for everything that only happens during an announcement:
RTP/SRTP, the report deferred until the stream has ended, stack headroom of
the streaming and report tasks. Without --audio the dongle finds no RTP
destination and hangs up without audio.

--talk SPEC (with --audio): also send a PCMA stream to the dongle, like a
caller's phone does (20 ms frames, quiet line noise), with "speech" (a loud
tone) in the windows of SPEC, seconds after the ACK: "2-3.5,9-10". Use it for
the interactive dialog (ab_mode=dialog): the dongle's VAD hears the tone as
speech and answers after the pause. Implies --max SECONDS of 120 unless given.

--mute-after SECONDS (with --talk): stop sending RTP at that point while
staying on the line — a caller whose media died. The dialog must hang up by
itself ~30 s later (no RTP from the caller).

--max SECONDS (with --audio): end the call with our BYE after that long
(default 120).

--bye-after SECONDS (with --audio): hang up like an impatient caller, i.e.
send BYE that many seconds after the ACK, while the announcement is still
playing. The call's duration_s in /api/calls (and the call webhook) then
shows when the caller left.

Note: send only the bare number (no display name). A non-numeric display
name trips is_known_contact() and the call skips the blocklist/API check
(and is dropped entirely when "log known calls" is off).
"""
import socket, sys, time

args = sys.argv[1:]
hang = "--hang" in args
if hang:
    args.remove("--hang")
audio = "--audio" in args
if audio:
    args.remove("--audio")
if hang and audio:
    sys.exit("--hang and --audio exclude each other")
bye_after = None
if "--bye-after" in args:
    i = args.index("--bye-after")
    try:
        bye_after = float(args[i + 1])
    except (IndexError, ValueError):
        sys.exit("--bye-after needs a number of seconds")
    del args[i:i + 2]
    if not audio:
        sys.exit("--bye-after needs --audio")
def take_opt(name, conv):
    if name not in args:
        return None
    i = args.index(name)
    try:
        v = conv(args[i + 1])
    except (IndexError, ValueError):
        sys.exit(f"{name} needs a value")
    del args[i:i + 2]
    if not audio:
        sys.exit(f"{name} needs --audio")
    return v

def parse_talk(spec):
    out = []
    for part in spec.split(","):
        a, b = part.split("-")
        out.append((float(a), float(b)))
    return out

talk = take_opt("--talk", parse_talk)
mute_after = take_opt("--mute-after", float)
max_secs = take_opt("--max", float) or 120.0
if mute_after is not None and talk is None:
    sys.exit("--mute-after needs --talk")
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
to_tag = ""                                  # the dongle's tag, from its 200 OK

sdp = ""
if audio:
    rtp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    rtp.bind((my_ip, 0))
    rtp.settimeout(0.02)
    sdp = (f"v=0\r\no=- {tag} 1 IN IP4 {my_ip}\r\ns=-\r\nc=IN IP4 {my_ip}\r\n"
           f"t=0 0\r\nm=audio {rtp.getsockname()[1]} RTP/AVP 8\r\n"
           f"a=rtpmap:8 PCMA/8000\r\na=sendrecv\r\n")

def msg(method, cseq=1, body=""):
    return (
        f"{method} sip:answerbot@{host} SIP/2.0\r\n"
        f"Via: SIP/2.0/UDP {my_ip}:{my_port};branch=z9hG4bK-fakecall-{tag}-{cseq}\r\n"
        f"Max-Forwards: 70\r\n"
        f"From: <sip:{caller}@{my_ip}>;tag=fakecall{tag}\r\n"
        f"To: <sip:answerbot@{host}>{to_tag}\r\n"
        f"Call-ID: fakecall-{tag}@{my_ip}\r\n"
        f"CSeq: {cseq} {method}\r\n"
        f"Contact: <sip:{caller}@{my_ip}:{my_port}>\r\n"
        + ("Content-Type: application/sdp\r\n" if body else "")
        + f"Content-Length: {len(body)}\r\n\r\n{body}"
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
        lines = data.decode(errors="replace").splitlines()
        line = lines[0]
        print(f"  <= [{label}] {line}")
        parts = line.split()
        if len(parts) >= 2 and parts[0] == "SIP/2.0" and parts[1].isdigit():
            code = int(parts[1])
            if code >= 200:
                final = code
                if code == 200 and any(l.lower().startswith("cseq:") and "INVITE" in l
                                       for l in lines):
                    remember_to_tag(lines)
                    remember_media(lines)
                    if audio:
                        break                # the stream starts right after
    return final

def remember_to_tag(lines):
    """In-dialog requests (BYE) must carry the dongle's To-tag."""
    global to_tag
    for l in lines:
        if l.lower().startswith(("to:", "t:")) and "tag=" in l:
            to_tag = ";tag=" + l.split("tag=", 1)[1].split(";")[0].strip()

remote_media = None                          # (ip, port) from the dongle's SDP answer

def remember_media(lines):
    global remote_media
    ip, port = None, None
    for l in lines:
        if l.startswith("c=IN IP4 "):
            ip = l.split()[2]
        elif l.startswith("m=audio "):
            port = int(l.split()[1])
    if ip and port:
        remote_media = (ip, port)

def alaw(sample):
    """G.711 A-law encode of one 16-bit linear sample."""
    sign = 0x80 if sample >= 0 else 0x00
    if sample < 0:
        sample = -sample - 1
    sample = min(sample, 32635) >> 3
    if sample >= 32:
        exp = sample.bit_length() - 5
        code = (exp << 4) | ((sample >> exp) & 0x0F)
    else:
        code = sample >> 1
    return (code | sign) ^ 0x55

import math
QUIET = bytes(alaw(int(30 * math.sin(i))) for i in range(160))           # ~ -60 dBFS
LOUD  = bytes(alaw(int(12000 * math.sin(2 * math.pi * 440 * i / 8000)))  # ~ -11 dBFS
              for i in range(160))

def receive_audio():
    """Count RTP packets until the dongle hangs up or the stream goes silent."""
    sock.settimeout(0.002)
    packets, first, last = 0, None, None
    end = time.time() + max_secs
    seq, ts, next_tx, sent = 0, 0, time.time(), 0
    while time.time() < end:
        now = time.time()
        if talk is not None and remote_media and now >= next_tx and \
                (mute_after is None or now - start < mute_after):
            t = now - start
            payload = LOUD if any(a <= t < b for a, b in talk) else QUIET
            hdr = bytes([0x80, 8, seq >> 8 & 0xFF, seq & 0xFF,
                         ts >> 24 & 0xFF, ts >> 16 & 0xFF, ts >> 8 & 0xFF, ts & 0xFF,
                         0x12, 0x34, 0x56, 0x78])
            rtp.sendto(hdr + payload, remote_media)
            seq = (seq + 1) & 0xFFFF; ts = (ts + 160) & 0xFFFFFFFF
            next_tx += 0.02; sent += 1
        elif mute_after is not None and sent and now - start >= mute_after and sent > 0:
            print(f"-> (--mute-after {mute_after:g} s) RTP stopped, staying on the line")
            sent = -1
        try:
            rtp.recv(2048)
            packets += 1
            last = time.time()
            first = first or last
        except socket.timeout:
            pass
        try:
            data = sock.recv(4096).decode(errors="replace")
            if data.startswith("BYE"):
                # Echo the dialog headers back in the 200 OK.
                keep = [l for l in data.splitlines() if l.lower().startswith(
                    ("via:", "from:", "to:", "call-id:", "cseq:"))]
                sock.send(("SIP/2.0 200 OK\r\n" + "\r\n".join(keep)
                           + "\r\nContent-Length: 0\r\n\r\n").encode())
                print("  <= [audio] BYE from dongle, answered 200")
                break
        except socket.timeout:
            pass
        if bye_after is not None and time.time() - start >= bye_after:
            print(f"-> BYE (--bye-after {bye_after:g} s)"); sock.send(msg("BYE", 2))
            break
        if talk is None and time.time() - (last or start) > 5:
            print("-> BYE (stream silent for 5 s)"); sock.send(msg("BYE", 2))
            break
    else:
        print(f"-> BYE (--max {max_secs:g} s)"); sock.send(msg("BYE", 2))
    span = (last - first) if first else 0.0
    print(f"RTP: {packets} packet(s) over {span:.1f} s, call {time.time() - start:.1f} s")

# Allow >3 s so the non-spam decline (480 after SIP_DECLINE_DELAY) is seen.
print("-> INVITE"); sock.send(msg("INVITE", 1, sdp)); final = drain("after INVITE", 3.5)
if hang:
    print("-> (--hang) leaving dialog open: no ACK/CANCEL sent")
else:
    # Tear the dialog down so the dongle's single slot is freed: ACK any final
    # response (2xx spam answer or 4xx decline), else CANCEL a still-ringing call.
    if final == 200 and audio:
        print("-> ACK (final 200), receiving audio"); sock.send(msg("ACK"))
        start = time.time()
        receive_audio()
    elif final is not None:
        print(f"-> ACK (final {final})"); sock.send(msg("ACK")); drain("after ACK", 1.5)
    else:
        print("-> CANCEL (still ringing)"); sock.send(msg("CANCEL")); drain("after CANCEL", 1.5)
sock.close()
print("done — check the call log on the dongle's web UI / GET /api/calls")
