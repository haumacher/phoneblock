package main

import (
	"testing"
	"time"
)

func TestExtractRequestMethodAndURI(t *testing.T) {
	tests := []struct {
		name           string
		logLine        string
		expectedMethod string
		expectedURI    string
	}{
		{
			name:           "GET request with challenge",
			logLine:        `192.168.1.100 - - [03/Nov/2025:10:15:30 +0000] "GET /page?challenge=abc123 HTTP/1.1" 200 1234 "-" "Mozilla/5.0"`,
			expectedMethod: "GET",
			expectedURI:    "/page?challenge=abc123",
		},
		{
			name:           "POST request with solution",
			logLine:        `192.168.1.100 - - [03/Nov/2025:10:16:45 +0000] "POST /api/submit?solution=xyz789 HTTP/1.1" 200 567 "-" "Mozilla/5.0"`,
			expectedMethod: "POST",
			expectedURI:    "/api/submit?solution=xyz789",
		},
		{
			name:           "GET request - Referer contains challenge but request does not",
			logLine:        `192.168.1.100 - - [03/Nov/2025:10:17:00 +0000] "GET /other-page HTTP/1.1" 200 890 "http://example.com/page?challenge=abc" "Mozilla/5.0"`,
			expectedMethod: "GET",
			expectedURI:    "/other-page",
		},
		{
			name:           "GET request with challenge and referer with challenge",
			logLine:        `192.168.1.100 - - [03/Nov/2025:10:18:00 +0000] "GET /page?challenge=new HTTP/1.1" 200 1234 "http://example.com/page?challenge=old" "Mozilla/5.0"`,
			expectedMethod: "GET",
			expectedURI:    "/page?challenge=new",
		},
		{
			name:           "GET request - Complex URI with multiple parameters",
			logLine:        `10.0.0.1 - - [04/Nov/2025:12:34:56 +0000] "GET /path?param1=value1&challenge=test&param2=value2 HTTP/1.1" 200 5000 "-" "curl/7.68.0"`,
			expectedMethod: "GET",
			expectedURI:    "/path?param1=value1&challenge=test&param2=value2",
		},
		{
			name:           "PUT request",
			logLine:        `10.0.0.1 - - [04/Nov/2025:12:34:56 +0000] "PUT /api/resource HTTP/1.1" 200 100 "-" "curl/7.68.0"`,
			expectedMethod: "PUT",
			expectedURI:    "/api/resource",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			method, uri := extractRequestMethodAndURI(tt.logLine)
			if method != tt.expectedMethod {
				t.Errorf("extractRequestMethodAndURI() method = %q, expected %q", method, tt.expectedMethod)
			}
			if uri != tt.expectedURI {
				t.Errorf("extractRequestMethodAndURI() URI = %q, expected %q", uri, tt.expectedURI)
			}
		})
	}
}

func TestProcessLogLine(t *testing.T) {
	tests := []struct {
		name                string
		logLine             string
		expectChallenge     bool
		expectSolution      bool
		shouldProcessLine   bool
	}{
		{
			name:                "GET request with challenge in URI",
			logLine:             `192.168.1.100 - - [03/Nov/2025:10:15:30 +0000] "GET /page?challenge=abc HTTP/1.1" 200 1234 "-" "Mozilla/5.0"`,
			expectChallenge:     true,
			expectSolution:      false,
			shouldProcessLine:   true,
		},
		{
			name:                "GET request with solution in URI",
			logLine:             `192.168.1.100 - - [03/Nov/2025:10:15:30 +0000] "GET /submit?solution=xyz HTTP/1.1" 200 567 "-" "Mozilla/5.0"`,
			expectChallenge:     false,
			expectSolution:      true,
			shouldProcessLine:   true,
		},
		{
			name:                "POST request with solution (should NOT be processed)",
			logLine:             `192.168.1.100 - - [03/Nov/2025:10:15:30 +0000] "POST /submit?solution=xyz HTTP/1.1" 200 567 "-" "Mozilla/5.0"`,
			expectChallenge:     false,
			expectSolution:      false,
			shouldProcessLine:   false,
		},
		{
			name:                "POST request with challenge (should NOT be processed)",
			logLine:             `192.168.1.100 - - [03/Nov/2025:10:15:30 +0000] "POST /page?challenge=abc HTTP/1.1" 200 1234 "-" "Mozilla/5.0"`,
			expectChallenge:     false,
			expectSolution:      false,
			shouldProcessLine:   false,
		},
		{
			name:                "Challenge only in referer (should NOT be detected)",
			logLine:             `192.168.1.100 - - [03/Nov/2025:10:15:30 +0000] "GET /page HTTP/1.1" 200 1234 "http://example.com?challenge=abc" "Mozilla/5.0"`,
			expectChallenge:     false,
			expectSolution:      false,
			shouldProcessLine:   false,
		},
		{
			name:                "Solution only in referer (should NOT be detected)",
			logLine:             `192.168.1.100 - - [03/Nov/2025:10:15:30 +0000] "GET /page HTTP/1.1" 200 1234 "http://example.com?solution=xyz" "Mozilla/5.0"`,
			expectChallenge:     false,
			expectSolution:      false,
			shouldProcessLine:   false,
		},
		{
			name:                "No challenge or solution",
			logLine:             `192.168.1.100 - - [03/Nov/2025:10:15:30 +0000] "GET /page HTTP/1.1" 200 1234 "-" "Mozilla/5.0"`,
			expectChallenge:     false,
			expectSolution:      false,
			shouldProcessLine:   false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			tracker := NewIPTracker()

			// Add a dummy challenge entry for solution test
			if tt.expectSolution {
				tracker.AddChallengeAccess("192.168.1.100", time.Now())
			}

			initialCount := tracker.GetChallengeCount()

			processLogLine(tt.logLine, tracker)

			finalCount := tracker.GetChallengeCount()

			if tt.shouldProcessLine {
				if tt.expectChallenge && finalCount <= initialCount {
					t.Errorf("Expected challenge to be tracked")
				}
				if tt.expectSolution && finalCount >= initialCount {
					t.Errorf("Expected IP to be removed after solution")
				}
			} else {
				if finalCount != initialCount {
					t.Errorf("Line should not have been processed, but count changed from %d to %d", initialCount, finalCount)
				}
			}
		})
	}
}

func TestExtractIP(t *testing.T) {
	tests := []struct {
		name     string
		logLine  string
		expected string
	}{
		{
			name:     "Standard IPv4",
			logLine:  `192.168.1.100 - - [03/Nov/2025:10:15:30 +0000] "GET /page HTTP/1.1" 200 1234`,
			expected: "192.168.1.100",
		},
		{
			name:     "Different IPv4",
			logLine:  `10.0.0.1 - - [03/Nov/2025:10:15:30 +0000] "GET /page HTTP/1.1" 200 1234`,
			expected: "10.0.0.1",
		},
		{
			name:     "IPv6 full format",
			logLine:  `2001:0db8:85a3:0000:0000:8a2e:0370:7334 - - [03/Nov/2025:10:15:30 +0000] "GET /page HTTP/1.1" 200 1234`,
			expected: "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
		},
		{
			name:     "IPv6 compressed format",
			logLine:  `2001:db8:85a3::8a2e:370:7334 - - [03/Nov/2025:10:15:30 +0000] "GET /page HTTP/1.1" 200 1234`,
			expected: "2001:db8:85a3::8a2e:370:7334",
		},
		{
			name:     "IPv6 localhost",
			logLine:  `::1 - - [03/Nov/2025:10:15:30 +0000] "GET /page HTTP/1.1" 200 1234`,
			expected: "::1",
		},
		{
			name:     "IPv6 with leading zeros omitted",
			logLine:  `2001:db8::1 - - [03/Nov/2025:10:15:30 +0000] "GET /page HTTP/1.1" 200 1234`,
			expected: "2001:db8::1",
		},
		{
			name:     "IPv6 fully compressed",
			logLine:  `fe80::1 - - [03/Nov/2025:10:15:30 +0000] "GET /page HTTP/1.1" 200 1234`,
			expected: "fe80::1",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := extractIP(tt.logLine)
			if result != tt.expected {
				t.Errorf("extractIP() = %q, expected %q", result, tt.expected)
			}
		})
	}
}

func TestExtractTimestamp(t *testing.T) {
	logLine := `192.168.1.100 - - [03/Nov/2025:10:15:30 +0000] "GET /page HTTP/1.1" 200 1234`
	result := extractTimestamp(logLine)

	expected := time.Date(2025, time.November, 3, 10, 15, 30, 0, time.UTC)

	if !result.Equal(expected) {
		t.Errorf("extractTimestamp() = %v, expected %v", result, expected)
	}
}
