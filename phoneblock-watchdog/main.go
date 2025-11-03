package main

import (
	"bufio"
	"flag"
	"fmt"
	"log"
	"os"
	"regexp"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/nxadm/tail"
)

type IPInfo struct {
	LastAccess        time.Time
	Count             int
	LastReportedCount int       // Count value when last reported
	LastReported      time.Time // Last time this IP was included in a status report
}

type IPTracker struct {
	mu               sync.RWMutex
	challengeIPs     map[string]*IPInfo // IPs that accessed challenge=
}

func NewIPTracker() *IPTracker {
	return &IPTracker{
		challengeIPs:     make(map[string]*IPInfo),
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
	}

	t, err := tail.TailFile(filename, config)
	if err != nil {
		return fmt.Errorf("failed to tail file: %w", err)
	}

	log.Printf("Starting to monitor log file: %s", filename)

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
	flag.Usage = func() {
		fmt.Fprintf(os.Stderr, "Usage: %s [options] <apache-access-log-file>\n\n", os.Args[0])
		fmt.Fprintf(os.Stderr, "Options:\n")
		flag.PrintDefaults()
	}
	flag.Parse()

	// Check that log file is provided
	if flag.NArg() < 1 {
		flag.Usage()
		os.Exit(1)
	}

	logFile := flag.Arg(0)
	tracker := NewIPTracker()

	log.SetOutput(os.Stdout)
	log.SetFlags(log.LstdFlags | log.Lmicroseconds)

	log.Printf("Starting with minimum access threshold: %d", *minAccesses)

	// Read existing log content
	if err := readExistingLog(logFile, tracker); err != nil {
		log.Printf("Warning: failed to read existing log: %v", err)
	}

	// Mark all IPs as reported before starting tail (to establish baseline)
	tracker.mu.Lock()
	for _, info := range tracker.challengeIPs {
		info.LastReportedCount = info.Count
	}
	tracker.mu.Unlock()

	// Print initial status (show all IPs)
	printStatus(tracker, true, *minAccesses)

	// Start periodic status reporting (every 1 minute, only show new activity)
	ticker := time.NewTicker(1 * time.Minute)
	go func() {
		for range ticker.C {
			printStatus(tracker, false, *minAccesses)
		}
	}()

	// Start tailing the log file
	if err := tailLogFile(logFile, tracker); err != nil {
		log.Fatalf("Failed to tail log file: %v", err)
	}
}
