# PhoneBlock Watchdog

A Go program that continuously monitors Apache access log files and tracks IP addresses that access challenge endpoints until they complete the solution.

## Features

- Continuously tails Apache access log files
- Supports both IPv4 and IPv6 addresses
- Tracks unique IPs accessing URLs containing `challenge=`
- Maintains access count and last access timestamp per IP
- Automatically removes IPs from tracking when they access URLs containing `solution=`
- Thread-safe IP tracking with concurrent access support
- Fast startup: `-limit` restricts the initial pass to the last N lines of the existing log file
- Automatically handles log file rotation
- Silent operation with periodic status reporting every minute
- Optional automatic firewall blocking using ufw
- Automatic unblocking after 30 minutes to prevent permanent blocks

## Installation

### Debian package (recommended for the server)

Every tag `watchdog-vX.Y.Z` builds Debian packages for `amd64`, `arm64` (aarch64) and `armhf`
(`.github/workflows/watchdog-release.yml`) and attaches them to the GitHub release. The binary is
static, so the same package runs on every Debian/Ubuntu release.

```bash
# release (developer machine)
git tag watchdog-v1.0.0 && git push origin watchdog-v1.0.0

# install / update (server)
V=1.0.0; A=$(dpkg --print-architecture)
curl -fLO https://github.com/haumacher/phoneblock/releases/download/watchdog-v$V/phoneblock-watchdog_${V}_$A.deb
sudo apt install ./phoneblock-watchdog_${V}_$A.deb
```

The package installs `/usr/bin/phoneblock-watchdog`, the systemd unit `phoneblock-watchdog.service`
(enabled and started on install, restarted on update) and the settings
`/etc/default/phoneblock-watchdog` (log file and options; kept on update):

```bash
sudo nano /etc/default/phoneblock-watchdog
sudo systemctl restart phoneblock-watchdog
sudo journalctl -u phoneblock-watchdog -f
```

**Migrating from a hand-installed service:** a unit in `/etc/systemd/system/` overrides the packaged
one (the package warns about it). Remove it after taking over its options into
`/etc/default/phoneblock-watchdog`:

```bash
sudo systemctl stop phoneblock-watchdog
sudo rm /etc/systemd/system/phoneblock-watchdog.service /usr/local/bin/phoneblock-watchdog
sudo systemctl daemon-reload
sudo systemctl restart phoneblock-watchdog
```

Build a package locally (needs `go` and `dpkg-deb`; output in `dist/`):

```bash
debian/build-deb.sh 1.0.0 arm64
```

### Build from source

```bash
go build -ldflags="-s -w" -o phoneblock-watchdog

# Cross-compile, e.g. for a Linux ARM64 server
GOOS=linux GOARCH=arm64 go build -o phoneblock-watchdog
```

## Usage

```bash
./phoneblock-watchdog [options] <apache-access-log-file>
```

### Options

- `-min-accesses <number>` - Minimum number of challenge accesses to trigger reporting (default: 5)
- `-enable-firewall` - Enable automatic firewall blocking using ufw (default: false)
- `-initial-block-minutes <number>` - First block duration; doubles on each re-block, max 30 days (default: 30)
- `-continuous` - Keep tailing the log file; without it the existing log is processed once and the program exits
- `-limit <number>` - Lines read from the end of the existing log at startup (default: 0 = whole file)

### Examples

Basic usage (default threshold of 5):
```bash
./phoneblock-watchdog /var/log/apache2/access.log
```

Custom threshold (report IPs with more than 10 accesses):
```bash
./phoneblock-watchdog -min-accesses 10 /var/log/apache2/access.log
```

Enable automatic firewall blocking:
```bash
sudo ./phoneblock-watchdog -enable-firewall /var/log/apache2/access.log
```

Combined options:
```bash
sudo ./phoneblock-watchdog -min-accesses 10 -enable-firewall /var/log/apache2/access.log
```

Show help:
```bash
./phoneblock-watchdog -h
```

## Graceful Shutdown

When you stop the program (Ctrl+C or `kill`), it automatically cleans up all firewall rules:

```
^CReceived signal: interrupt
Shutting down gracefully...
Cleaning up firewall rules...
[FIREWALL] Unblocked IP: 192.168.1.101
[FIREWALL] Unblocked IP: 192.168.1.102
Removed 2 firewall rules
Shutdown complete
```

**Note**: The program handles `SIGINT` (Ctrl+C) and `SIGTERM` (kill) signals gracefully.

## Running in Background

Use the Debian package (see *Installation*), which runs the watchdog as a systemd service. Note that
the watchdog only keeps monitoring with `-continuous`; without it, it processes the existing log once
and exits.

## How It Works

1. **GET Requests Only**: Only GET requests are processed; POST, PUT, and other methods are ignored
2. **API Requests Ignored**: Requests whose path contains `/api/` are skipped. The server never challenges API calls, and counting them would ban API clients (e.g. dongles) and everyone sharing their NAT address
3. **Request URI Parsing**: Extracts the request URI from the HTTP request field (not from referer or user-agent)
4. **Challenge Access**: When a GET request URI contains `challenge=`, the IP is added to the tracking set (or access count incremented if already tracked)
5. **Solution Access**: When a GET request URI contains `solution=`, the IP is removed from the tracking set
6. **Tracking**: Each IP stores:
   - Total number of challenge accesses
   - Timestamp of the most recent access
7. **Status Reports**:
   - **Initial report**: Shows all IPs exceeding the minimum access threshold
   - **Periodic reports** (every minute): Shows only IPs with NEW accesses since last report (and exceeding the threshold)
   - Threshold is configurable via `-min-accesses` flag (default: 5)
8. **Firewall Management** (optional, requires `-enable-firewall` flag):
   - Automatically blocks IPs exceeding the threshold using `ufw deny from <ip>`
   - Blocks are automatically removed after 30 minutes
   - Requires root/sudo privileges to manage firewall rules

## Output

The program runs silently and only prints status summaries:

### Initial Status (on startup)
```
Starting with minimum access threshold: 5
Firewall management: ENABLED (30 minute block duration)
Processed 5000 existing log lines (last 5000 of 15234 total)
[FIREWALL] Blocked IP: 192.168.1.101
[FIREWALL] Blocked IP: 192.168.1.102
[FIREWALL] Blocked IP: 192.168.1.103
=== STATUS: 12 unique IPs currently tracked for challenge access ===
  - 192.168.1.101: 8 accesses, last: 2025-11-03T10:20:00Z
  - 192.168.1.102: 15 accesses, last: 2025-11-03T10:18:30Z
  - 192.168.1.103: 6 accesses, last: 2025-11-03T10:19:15Z
=== 3 IPs with more than 5 challenge accesses ===
```

**Notes**:
- With `-limit 5000`, only the last 5000 lines of the existing log file are processed on startup
- Firewall blocking messages only appear when `-enable-firewall` is used

### Periodic Status (every minute)
```
[FIREWALL] Blocked IP: 192.168.1.104
=== STATUS: 15 unique IPs currently tracked for challenge access ===
  - 192.168.1.101: 12 accesses, last: 2025-11-03T10:21:00Z
  - 192.168.1.104: 7 accesses, last: 2025-11-03T10:20:45Z
=== 2 IPs with new accesses (>5 total) ===
```

After 30 minutes:
```
[FIREWALL] Unblocked IP: 192.168.1.101
=== STATUS: 14 unique IPs currently tracked for challenge access ===
```

**Notes**:
- Periodic reports only show IPs that have had new activity since the last report
- Only IPs with more than the threshold total challenge accesses are shown
- If no IPs have new activity, the report shows only the total count
- IPs are automatically unblocked after 30 minutes

## Apache Log Format

The program expects standard Apache Combined Log Format:

```
IP - - [timestamp] "REQUEST" status size "referer" "user-agent"
```

Example with IPv4:
```
192.168.1.100 - - [03/Nov/2025:10:15:30 +0000] "GET /page?challenge=abc123 HTTP/1.1" 200 1234 "-" "Mozilla/5.0"
192.168.1.100 - - [03/Nov/2025:10:16:45 +0000] "GET /page?solution=xyz789 HTTP/1.1" 200 567 "-" "Mozilla/5.0"
```

Example with IPv6:
```
2001:db8:85a3::8a2e:370:7334 - - [03/Nov/2025:10:15:30 +0000] "GET /page?challenge=abc123 HTTP/1.1" 200 1234 "-" "Mozilla/5.0"
2001:db8:85a3::8a2e:370:7334 - - [03/Nov/2025:10:16:45 +0000] "GET /page?solution=xyz789 HTTP/1.1" 200 567 "-" "Mozilla/5.0"
```

**Note**: Only GET requests are tracked. The program will ignore POST, PUT, and other HTTP methods, even if they contain `challenge=` or `solution=` patterns.

## Implementation Details

- Uses `github.com/nxadm/tail` for efficient log tailing
- Extracts IPv4 and IPv6 addresses using regex patterns
- Supports all IPv6 formats: full, compressed, and :: notation
- Parses Apache timestamp format (dd/MMM/yyyy:HH:mm:ss +0000)
- Maintains in-memory map of unique IPs with their access information
- Thread-safe operations with RWMutex protection
- Background goroutine for periodic status reporting
