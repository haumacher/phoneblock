# PhoneBlock Watchdog

A Go program that continuously monitors Apache access log files and tracks IP addresses that access challenge endpoints until they complete the solution.

## Features

- Continuously tails Apache access log files
- Supports both IPv4 and IPv6 addresses
- Tracks unique IPs accessing URLs containing `challenge=`
- Maintains access count and last access timestamp per IP
- Automatically removes IPs from tracking when they access URLs containing `solution=`
- Thread-safe IP tracking with concurrent access support
- Fast startup: processes only the last 5000 lines of existing log file
- Automatically handles log file rotation
- Silent operation with periodic status reporting every minute

## Installation

### Build from Source

```bash
# Download dependencies
go mod download

# Build for current system
go build -o phoneblock-watchdog

# Or build optimized (smaller binary)
go build -ldflags="-s -w" -o phoneblock-watchdog
```

### Cross-Compilation for Server

If building on one machine to deploy on another:

```bash
# For Linux 64-bit server (most common)
GOOS=linux GOARCH=amd64 go build -o phoneblock-watchdog

# For Linux ARM64 (e.g., Raspberry Pi)
GOOS=linux GOARCH=arm64 go build -o phoneblock-watchdog

# For macOS (Intel)
GOOS=darwin GOARCH=amd64 go build -o phoneblock-watchdog

# For macOS (Apple Silicon)
GOOS=darwin GOARCH=arm64 go build -o phoneblock-watchdog
```

After building, transfer the `phoneblock-watchdog` binary to your server and make it executable:
```bash
chmod +x phoneblock-watchdog
```

## Usage

```bash
./phoneblock-watchdog [options] <apache-access-log-file>
```

### Options

- `-min-accesses <number>` - Minimum number of challenge accesses to trigger reporting (default: 5)

### Examples

Basic usage (default threshold of 5):
```bash
./phoneblock-watchdog /var/log/apache2/access.log
```

Custom threshold (report IPs with more than 10 accesses):
```bash
./phoneblock-watchdog -min-accesses 10 /var/log/apache2/access.log
```

Show help:
```bash
./phoneblock-watchdog -h
```

## How It Works

1. **GET Requests Only**: Only GET requests are processed; POST, PUT, and other methods are ignored
2. **Request URI Parsing**: Extracts the request URI from the HTTP request field (not from referer or user-agent)
3. **Challenge Access**: When a GET request URI contains `challenge=`, the IP is added to the tracking set (or access count incremented if already tracked)
4. **Solution Access**: When a GET request URI contains `solution=`, the IP is removed from the tracking set
5. **Tracking**: Each IP stores:
   - Total number of challenge accesses
   - Timestamp of the most recent access
6. **Status Reports**:
   - **Initial report**: Shows all IPs exceeding the minimum access threshold
   - **Periodic reports** (every minute): Shows only IPs with NEW accesses since last report (and exceeding the threshold)
   - Threshold is configurable via `-min-accesses` flag (default: 5)

## Output

The program runs silently and only prints status summaries:

### Initial Status (on startup)
```
Starting with minimum access threshold: 5
Processed 5000 existing log lines (last 5000 of 15234 total)
=== STATUS: 12 unique IPs currently tracked for challenge access ===
  - 192.168.1.101: 8 accesses, last: 2025-11-03T10:20:00Z
  - 192.168.1.102: 15 accesses, last: 2025-11-03T10:18:30Z
  - 192.168.1.103: 6 accesses, last: 2025-11-03T10:19:15Z
=== 3 IPs with more than 5 challenge accesses ===
```

**Note**: Only the last 5000 lines of the existing log file are processed on startup for faster initialization.

### Periodic Status (every minute)
```
=== STATUS: 15 unique IPs currently tracked for challenge access ===
  - 192.168.1.101: 12 accesses, last: 2025-11-03T10:21:00Z
  - 192.168.1.104: 7 accesses, last: 2025-11-03T10:20:45Z
=== 2 IPs with new accesses (>5 total) ===
```

**Note**:
- Periodic reports only show IPs that have had new activity since the last report
- Only IPs with more than 5 total challenge accesses are shown
- If no IPs have new activity, the report shows only the total count

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
