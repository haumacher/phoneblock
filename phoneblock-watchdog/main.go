package main

import (
	"bufio"
	"flag"
	"fmt"
	"io"
	"log"
	"os"
	"os/exec"
	"os/signal"
	"regexp"
	"sort"
	"strings"
	"sync"
	"syscall"
	"time"

	"github.com/nxadm/tail"
)

type IPInfo struct {
	LastAccess        time.Time
	Count             int
	LastReportedCount int           // Count value when last reported
	LastReported      time.Time     // Last time this IP was included in a status report
	Blocked           bool          // Whether this IP is currently blocked by firewall
	BlockedAt         time.Time     // When this IP was blocked
	LastBlockCheck    int           // Count value when last checked for blocking
	BlockDuration     time.Duration // Current block duration (doubles on each re-block)
}

type IPTracker struct {
	mu               sync.RWMutex
	challengeIPs     map[string]*IPInfo // IPs that accessed challenge=
	firewallEnabled  bool               // Whether to manage firewall rules
}

func NewIPTracker(firewallEnabled bool) *IPTracker {
	return &IPTracker{
		challengeIPs:    make(map[string]*IPInfo),
		firewallEnabled: firewallEnabled,
	}
}

// AddChallengeAccess records or updates a challenge access for an IP
func (t *IPTracker) AddChallengeAccess(ip string, timestamp time.Time) {
	t.mu.Lock()
	defer t.mu.Unlock()

	if info, exists := t.challengeIPs[ip]; exists {
		// Update existing entry
		info.Count++
		info.LastAccess = timestamp
	} else {
		// New entry
		t.challengeIPs[ip] = &IPInfo{
			LastAccess: timestamp,
			Count:      1,
		}
	}
}

// AddSolutionAccess records a solution access and removes the IP from challenge tracking
func (t *IPTracker) AddSolutionAccess(ip string, timestamp time.Time) {
	t.mu.Lock()
	defer t.mu.Unlock()

	if _, exists := t.challengeIPs[ip]; exists {
		delete(t.challengeIPs, ip)
	}
}

// GetChallengeIPs returns a copy of all IPs currently tracked for challenge accesses
func (t *IPTracker) GetChallengeIPs() map[string]IPInfo {
	t.mu.RLock()
	defer t.mu.RUnlock()

	// Return a copy
	result := make(map[string]IPInfo)
	for ip, info := range t.challengeIPs {
		result[ip] = *info
	}
	return result
}

// GetChallengeCount returns the number of unique IPs currently tracked
func (t *IPTracker) GetChallengeCount() int {
	t.mu.RLock()
	defer t.mu.RUnlock()
	return len(t.challengeIPs)
}

// parseUFWOutput parses UFW status output and returns blocked IPs (deduplicated)
func parseUFWOutput(output string) []string {
	// Use a map to deduplicate IPs
	seenIPs := make(map[string]bool)
	lines := strings.Split(output, "\n")

	// Look for lines like: "[ 1] DENY IN            192.168.1.1"
	// or: "[ 1] Anywhere                   DENY IN     192.168.1.1"
	denyRegex := regexp.MustCompile(`DENY.*?(?:from\s+)?([0-9a-fA-F:\.]+)`)

	for _, line := range lines {
		if !strings.Contains(line, "DENY") {
			continue
		}

		matches := denyRegex.FindStringSubmatch(line)
		if len(matches) > 1 {
			ip := matches[1]
			// Validate it's a real IP (not "Anywhere" or other text)
			if (strings.Contains(ip, ".") || strings.Contains(ip, ":")) && ip != "0.0.0.0" {
				seenIPs[ip] = true
			}
		}
	}

	// Convert map to slice
	var blockedIPs []string
	for ip := range seenIPs {
		blockedIPs = append(blockedIPs, ip)
	}

	return blockedIPs
}

// readExistingFirewallRules reads existing UFW rules and returns blocked IPs
func readExistingFirewallRules() ([]string, error) {
	out, err := exec.Command("ufw", "status", "numbered").CombinedOutput()
	if err != nil {
		return nil, fmt.Errorf("failed to read ufw status: %w, output: %s", err, string(out))
	}

	return parseUFWOutput(string(out)), nil
}

// blockIP blocks an IP using ufw
func blockIP(ip string) error {
	cmd := fmt.Sprintf("ufw deny from %s", ip)
	out, err := exec.Command("sh", "-c", cmd).CombinedOutput()
	if err != nil {
		return fmt.Errorf("failed to block IP %s: %w, output: %s", ip, err, string(out))
	}
	log.Printf("[FIREWALL] Blocked IP: %s", ip)
	return nil
}

// unblockIP unblocks an IP using ufw
func unblockIP(ip string) error {
	cmd := fmt.Sprintf("ufw delete deny from %s", ip)
	out, err := exec.Command("sh", "-c", cmd).CombinedOutput()
	if err != nil {
		return fmt.Errorf("failed to unblock IP %s: %w, output: %s", ip, err, string(out))
	}
	log.Printf("[FIREWALL] Unblocked IP: %s", ip)
	return nil
}

// ManageFirewallRules checks all IPs and manages firewall rules
func (t *IPTracker) ManageFirewallRules(threshold int, initialBlockDuration time.Duration) {
	if !t.firewallEnabled {
		return
	}

	t.mu.Lock()
	defer t.mu.Unlock()

	now := time.Now()
	maxBlockDuration := 30 * 24 * time.Hour // 1 month
	maxUnblocksPerCycle := 10
	unblockedCount := 0

	for ip, info := range t.challengeIPs {
		// Check if IP should be unblocked
		// Limit to maxUnblocksPerCycle per run
		if info.Blocked && now.Sub(info.BlockedAt) >= info.BlockDuration && unblockedCount < maxUnblocksPerCycle {
			if err := unblockIP(ip); err != nil {
				log.Printf("Error unblocking IP %s: %v", ip, err)
			} else {
				info.Blocked = false
				info.BlockedAt = time.Time{}
				// Update LastBlockCheck to current count to prevent immediate re-blocking
				info.LastBlockCheck = info.Count
				unblockedCount++
				// Keep BlockDuration for next time (it will be doubled if blocked again)
			}
		}

		// Check if IP should be blocked:
		// - Not already blocked
		// - Count exceeds threshold
		// - Has NEW accesses since last block check (Count > LastBlockCheck)
		if !info.Blocked && info.Count > threshold && info.Count > info.LastBlockCheck {
			if err := blockIP(ip); err != nil {
				log.Printf("Error blocking IP %s: %v", ip, err)
			} else {
				info.Blocked = true
				info.BlockedAt = now
				// Update LastBlockCheck when blocking
				info.LastBlockCheck = info.Count

				// Calculate new block duration (exponential backoff)
				if info.BlockDuration == 0 {
					// First time blocking this IP
					info.BlockDuration = initialBlockDuration
				} else {
					// Double the duration, up to max
					info.BlockDuration = info.BlockDuration * 2
					if info.BlockDuration > maxBlockDuration {
						info.BlockDuration = maxBlockDuration
					}
				}
			}
		}
	}
}

// LoadExistingFirewallRules reads UFW rules and marks IPs as blocked
func (t *IPTracker) LoadExistingFirewallRules(initialBlockDuration time.Duration, threshold int) {
	if !t.firewallEnabled {
		return
	}

	blockedIPs, err := readExistingFirewallRules()
	if err != nil {
		log.Printf("Warning: failed to read existing firewall rules: %v", err)
		return
	}

	if len(blockedIPs) == 0 {
		log.Printf("No existing firewall rules found")
		return
	}

	t.mu.Lock()
	defer t.mu.Unlock()

	now := time.Now()
	count := 0

	for _, ip := range blockedIPs {
		// Check if this IP is already being tracked
		if info, exists := t.challengeIPs[ip]; exists {
			// IP is already in our tracking, mark it as blocked
			info.Blocked = true
			info.BlockedAt = now
			info.BlockDuration = initialBlockDuration
			// Ensure count is at least the threshold (if it's lower, set to threshold+1)
			if info.Count <= threshold {
				info.Count = threshold + 1
			}
			count++
		} else {
			// IP is in firewall but not in our tracking
			// Add it to tracking so it can be managed
			// Set count to threshold+1 to be consistent with blocking logic
			t.challengeIPs[ip] = &IPInfo{
				LastAccess:    now,
				Count:         threshold + 1, // Must be > threshold to be blocked
				Blocked:       true,
				BlockedAt:     now,
				BlockDuration: initialBlockDuration,
			}
			count++
		}
	}

	log.Printf("Loaded %d existing firewall rules", count)
}

// Extract IP from Apache access log line
// Supports both IPv4 and IPv6 addresses
// Common formats: "IP - - [timestamp]" or "IP - user [timestamp]"
func extractIP(line string) string {
	// Try to match IPv6 at the beginning of the line
	// IPv6 format: 2001:0db8:85a3:0000:0000:8a2e:0370:7334 or compressed forms
	// Apache often logs IPv6 without brackets or with brackets
	ipv6Regex := regexp.MustCompile(`^([0-9a-fA-F:]+:+[0-9a-fA-F:]*)\s`)
	matches := ipv6Regex.FindStringSubmatch(line)
	if len(matches) > 1 {
		// Basic validation: IPv6 must contain at least 2 colons
		if strings.Count(matches[1], ":") >= 2 {
			return matches[1]
		}
	}

	// Try to match IPv4 at the beginning of the line
	ipv4Regex := regexp.MustCompile(`^(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})`)
	matches = ipv4Regex.FindStringSubmatch(line)
	if len(matches) > 1 {
		return matches[1]
	}

	// Fallback: try to find any IPv4 in the line
	ipv4Regex = regexp.MustCompile(`(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})`)
	matches = ipv4Regex.FindStringSubmatch(line)
	if len(matches) > 1 {
		return matches[1]
	}

	return "unknown"
}

// Extract timestamp from Apache access log line
// Format: [dd/MMM/yyyy:HH:mm:ss +0000]
func extractTimestamp(line string) time.Time {
	timestampRegex := regexp.MustCompile(`\[([^\]]+)\]`)
	matches := timestampRegex.FindStringSubmatch(line)
	if len(matches) > 1 {
		// Parse Apache log timestamp format
		t, err := time.Parse("02/Jan/2006:15:04:05 -0700", matches[1])
		if err == nil {
			return t
		}
	}

	// Fallback to current time if parsing fails
	return time.Now()
}

// Extract request method and URI from Apache access log line
// Apache Combined Log Format: IP - - [timestamp] "METHOD URI PROTOCOL" status size "referer" "user-agent"
// This extracts the method and URI from the request field (not from the referer field)
// Returns method and URI, or empty strings if not found
func extractRequestMethodAndURI(line string) (string, string) {
	// Match the request field: "METHOD URI PROTOCOL"
	// The request is enclosed in quotes and comes after the timestamp
	requestRegex := regexp.MustCompile(`"\s*([A-Z]+)\s+([^\s]+)\s+HTTP/[^"]*"`)
	matches := requestRegex.FindStringSubmatch(line)
	if len(matches) > 2 {
		return matches[1], matches[2] // Return method and URI
	}

	return "", ""
}

func processLogLine(line string, tracker *IPTracker) {
	// Extract the request method and URI from the log line
	method, requestURI := extractRequestMethodAndURI(line)
	if requestURI == "" {
		return
	}

	// Only process GET requests
	if method != "GET" {
		return
	}

	// Check for challenge or solution patterns in the request URI only
	hasChallengePattern := strings.Contains(requestURI, "challenge=")
	hasSolutionPattern := strings.Contains(requestURI, "solution=")

	if !hasChallengePattern && !hasSolutionPattern {
		return
	}

	ip := extractIP(line)
	timestamp := extractTimestamp(line)

	// Process solution first, as it removes the IP from tracking
	if hasSolutionPattern {
		tracker.AddSolutionAccess(ip, timestamp)
	}

	// Then process challenge (if both are present, IP was already removed by solution)
	if hasChallengePattern {
		tracker.AddChallengeAccess(ip, timestamp)
	}
}

func tailLogFile(filename string, tracker *IPTracker) error {
	config := tail.Config{
		Follow:    true,
		ReOpen:    true,
		MustExist: false,
		Poll:      true,
		Location:  &tail.SeekInfo{Offset: 0, Whence: io.SeekEnd}, // Start from end of file
	}

	t, err := tail.TailFile(filename, config)
	if err != nil {
		return fmt.Errorf("failed to tail file: %w", err)
	}

	log.Printf("Starting to monitor log file for new entries: %s", filename)

	for line := range t.Lines {
		if line.Err != nil {
			log.Printf("Error reading line: %v", line.Err)
			continue
		}
		processLogLine(line.Text, tracker)
	}

	return nil
}

// Read existing log file content before tailing
func readExistingLog(filename string, tracker *IPTracker) error {
	file, err := os.Open(filename)
	if err != nil {
		if os.IsNotExist(err) {
			log.Printf("Log file doesn't exist yet, will wait for it: %s", filename)
			return nil
		}
		return fmt.Errorf("failed to open file: %w", err)
	}
	defer file.Close()

	// Use a circular buffer to keep only the last 5000 lines in memory
	const maxLines = 5000
	lines := make([]string, maxLines)
	writeIndex := 0
	totalLines := 0
	wrapped := false

	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		lines[writeIndex] = scanner.Text()
		writeIndex++
		totalLines++

		// When we reach maxLines, wrap around to the beginning
		if writeIndex >= maxLines {
			writeIndex = 0
			wrapped = true
		}
	}

	if err := scanner.Err(); err != nil {
		return fmt.Errorf("error reading file: %w", err)
	}

	// Process the lines in the correct order
	processedCount := 0
	if wrapped {
		// If we wrapped, start from writeIndex (oldest line) and process maxLines
		for i := 0; i < maxLines; i++ {
			idx := (writeIndex + i) % maxLines
			processLogLine(lines[idx], tracker)
			processedCount++
		}
	} else {
		// If we didn't wrap, process from 0 to writeIndex
		for i := 0; i < writeIndex; i++ {
			processLogLine(lines[i], tracker)
			processedCount++
		}
	}

	log.Printf("Processed %d existing log lines (last %d of %d total)", processedCount, processedCount, totalLines)
	return nil
}

func printStatus(tracker *IPTracker, showAll bool, minAccesses int) {
	tracker.mu.Lock()
	defer tracker.mu.Unlock()

	count := len(tracker.challengeIPs)
	log.Printf("=== STATUS: %d unique IPs currently tracked for challenge access ===", count)

	if count > 0 {
		now := time.Now()

		// Collect IPs to display
		type ipDisplay struct {
			ip   string
			info *IPInfo
		}
		var toDisplay []ipDisplay

		for ip, info := range tracker.challengeIPs {
			// Only show IPs with more than minAccesses accesses
			if info.Count <= minAccesses {
				continue
			}

			// Show if:
			// 1. showAll is true (initial report), OR
			// 2. IP has new accesses since last report (count increased)
			hasNewActivity := info.Count > info.LastReportedCount

			if showAll || hasNewActivity {
				toDisplay = append(toDisplay, ipDisplay{ip: ip, info: info})
			}
		}

		// Sort IPs alphabetically
		sort.Slice(toDisplay, func(i, j int) bool {
			return toDisplay[i].ip < toDisplay[j].ip
		})

		// Print sorted IPs
		newActivityCount := 0
		for _, item := range toDisplay {
			log.Printf("  - %s: %d accesses, last: %s", item.ip, item.info.Count, item.info.LastAccess.Format(time.RFC3339))

			hasNewActivity := item.info.Count > item.info.LastReportedCount
			if hasNewActivity {
				newActivityCount++
			}

			// Update last reported time and count for displayed IPs
			item.info.LastReported = now
			item.info.LastReportedCount = item.info.Count
		}

		if len(toDisplay) > 0 {
			if showAll {
				log.Printf("=== %d IPs with more than %d challenge accesses ===", len(toDisplay), minAccesses)
			} else {
				log.Printf("=== %d IPs with new accesses (>%d total) ===", newActivityCount, minAccesses)
			}
		}
	}
}

func main() {
	// Define command line flags
	minAccesses := flag.Int("min-accesses", 5, "Minimum number of challenge accesses to trigger reporting")
	enableFirewall := flag.Bool("enable-firewall", false, "Enable automatic firewall blocking using ufw")
	initialBlockMinutes := flag.Int("initial-block-minutes", 30, "Initial blocking duration in minutes (doubles on each re-block, max 30 days)")
	continuousMode := flag.Bool("continuous", false, "Enable continuous mode (tail log file and monitor indefinitely)")
	flag.Usage = func() {
		fmt.Fprintf(os.Stderr, "Usage: %s [options] <apache-access-log-file>\n\n", os.Args[0])
		fmt.Fprintf(os.Stderr, "Options:\n")
		flag.PrintDefaults()
		fmt.Fprintf(os.Stderr, "\nModes:\n")
		fmt.Fprintf(os.Stderr, "  Without --continuous: Process existing log file and exit\n")
		fmt.Fprintf(os.Stderr, "  With --continuous:    Monitor log file continuously\n")
	}
	flag.Parse()

	// Check that log file is provided
	if flag.NArg() < 1 {
		flag.Usage()
		os.Exit(1)
	}

	logFile := flag.Arg(0)
	tracker := NewIPTracker(*enableFirewall)

	log.SetOutput(os.Stdout)
	log.SetFlags(log.LstdFlags | log.Lmicroseconds)

	log.Printf("Starting with minimum access threshold: %d", *minAccesses)
	if *enableFirewall {
		log.Printf("Firewall management: ENABLED (initial block: %d minutes, doubles on re-block, max: 30 days)", *initialBlockMinutes)
	} else {
		log.Printf("Firewall management: DISABLED")
	}

	initialBlockDuration := time.Duration(*initialBlockMinutes) * time.Minute

	// Read existing log content
	if err := readExistingLog(logFile, tracker); err != nil {
		log.Printf("Warning: failed to read existing log: %v", err)
	}

	// Load existing firewall rules from previous runs
	tracker.LoadExistingFirewallRules(initialBlockDuration, *minAccesses)

	// Mark all IPs as reported before starting tail (to establish baseline)
	tracker.mu.Lock()
	for _, info := range tracker.challengeIPs {
		info.LastReportedCount = info.Count
		info.LastBlockCheck = info.Count
	}
	tracker.mu.Unlock()

	// Initial firewall management
	tracker.ManageFirewallRules(*minAccesses, initialBlockDuration)

	// Print initial status (show all IPs)
	printStatus(tracker, true, *minAccesses)

	// If not in continuous mode, exit after processing existing log
	if !*continuousMode {
		log.Printf("One-time processing complete. Use --continuous to monitor log file continuously.")
		os.Exit(0)
	}

	// Continuous mode: monitor log file indefinitely
	log.Printf("Continuous mode enabled - monitoring log file...")

	// Setup signal handling for graceful shutdown
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)

	// Start periodic status reporting and firewall management (every 1 minute)
	ticker := time.NewTicker(1 * time.Minute)
	go func() {
		for range ticker.C {
			tracker.ManageFirewallRules(*minAccesses, initialBlockDuration)
			printStatus(tracker, false, *minAccesses)
		}
	}()

	// Start tailing the log file in a goroutine
	done := make(chan error, 1)
	go func() {
		done <- tailLogFile(logFile, tracker)
	}()

	// Wait for either an error or a signal
	select {
	case err := <-done:
		log.Fatalf("Log tailing stopped: %v", err)
	case sig := <-sigChan:
		log.Printf("\nReceived signal: %v", sig)
		log.Printf("Shutting down gracefully...")
		ticker.Stop()
		log.Printf("Firewall rules remain active (will be reloaded on next startup)")
		log.Printf("Shutdown complete")
		os.Exit(0)
	}
}
