#!/usr/bin/env bash
#
# pb-log-summary.sh - Group and count warnings/errors in the PhoneBlock server log.
#
# The web application logs via tinylog to a rolling file using the format
#   [{date}] {level}: [{class}]: {message}
# (see phoneblock/src/main/java/tinylog.properties). This script normalizes away
# the timestamp and variable parts of each message (hashes, phone numbers, ids)
# so that otherwise identical messages collapse into a single counted group.
# The result is a quick "is anything running hot?" overview.
#
# Usage:
#   pb-log-summary.sh [options]
#
# Options:
#   -f FILE     Log file glob (default: /var/log/tomcat10/phoneblock.log*).
#               Quote it so the shell does not expand the glob, e.g.
#               -f '/var/log/tomcat10/phoneblock.log*'.
#   -l LEVEL    Levels to include as an alternation (default: ERROR|WARN).
#               Examples: -l ERROR   or   -l 'ERROR|WARN|INFO'.
#   -d DATE     Only lines whose timestamp starts with DATE (prefix match on the
#               date field). Examples: -d 2026-06-14  or  -d '2026-06'.
#   -b          Group by component (class) instead of by message.
#   -n N        Show only the top N groups (default: 40; 0 = no limit).
#   -h          Show this help.
#
# Examples:
#   pb-log-summary.sh                          # top error/warn message groups
#   pb-log-summary.sh -b                       # which components complain most
#   pb-log-summary.sh -l ERROR -d $(date +%F)  # today's errors only
#   pb-log-summary.sh -f '/var/log/tomcat10/phoneblock.log' # current file only

set -euo pipefail

FILES='/var/log/tomcat10/phoneblock.log*'
LEVELS='ERROR|WARN'
DATE=''
BY_CLASS=0
TOP=40

usage() {
	# Print the leading comment block (without the shebang) as help text.
	sed -n '2,/^$/ s/^# \{0,1\}//p' "$0"
}

while getopts 'f:l:d:bn:h' opt; do
	case "$opt" in
		f) FILES="$OPTARG" ;;
		l) LEVELS="$OPTARG" ;;
		d) DATE="$OPTARG" ;;
		b) BY_CLASS=1 ;;
		n) TOP="$OPTARG" ;;
		h) usage; exit 0 ;;
		*) usage; exit 2 ;;
	esac
done

# Expand the (possibly quoted) glob into an array of existing files.
shopt -s nullglob
# shellcheck disable=SC2206
matches=( $FILES )
shopt -u nullglob
if [ ${#matches[@]} -eq 0 ]; then
	# Distinguish "directory not accessible" (needs sudo / wrong host) from a
	# genuinely empty match -- an empty glob looks the same in both cases.
	dir=$(dirname -- "$FILES")
	if [ ! -d "$dir" ]; then
		echo "pb-log-summary: directory does not exist: $dir" >&2
		echo "  Running locally? Point -f at the log copy, e.g. -f 'tmp/pb-logs/phoneblock.log*'." >&2
	elif [ ! -r "$dir" ] || [ ! -x "$dir" ]; then
		echo "pb-log-summary: cannot read directory (permission denied): $dir" >&2
		echo "  Tomcat logs are usually not world-readable -- retry with: sudo $0 $*" >&2
	else
		echo "pb-log-summary: no log files match: $FILES" >&2
	fi
	exit 1
fi

# Build the record filter: keep only WARN/ERROR head lines (those starting with
# a "[date] LEVEL:" prefix), optionally restricted to a date prefix.
record_re="^\[${DATE}[^]]*\] (${LEVELS}):"

# Normalization for message grouping: replace the variable parts of a message
# with stable placeholders so that records differing only in ids/numbers/dates
# collapse into a single counted group. Rules are ordered -- earlier ones see
# the raw digits the later ones would consume. perl (not sed) is required for
# the <TOKEN> rule, whose lookaheads express "contains lower AND upper AND
# digit" -- the signature of a random credential token that has no other tell.
normalize='
	s/^\[[^]]*\] //;                                                                                    # drop "[date] " prefix
	s/\x27[^\x27]*\x27/\x27<ARG>\x27/g;                                                                  # single-quoted value -> one slot (a quoted log arg is almost always one source-template argument)
	s/"[^"]*"/"<ARG>"/g;                                                                                 # double-quoted value -> one slot
	s/\b(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun) (?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) +\d+ \d+:\d+:\d+ \w+ \d{4}\b/<DATE>/g;  # java Date.toString()
	s/\[(?:[0-9a-fA-F]{0,4}:){2,}[0-9a-fA-F]{0,4}\]/[<IP6>]/g;                                           # bracketed IPv6
	s/\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b/<IP>/g;                                                     # IPv4
	s/\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b/<UUID>/g;          # UUID (user id)
	s/\b(?=[A-Za-z0-9]{12,}\b)(?=[A-Za-z0-9]*[a-z])(?=[A-Za-z0-9]*[A-Z])(?=[A-Za-z0-9]*\d)[A-Za-z0-9]+\b/<TOKEN>/g;  # random credential token
	s/\b(?=[0-9a-fA-F]{8,}\b)[0-9a-fA-F]*[a-fA-F][0-9a-fA-F]*\b/<HEX>/g;                                 # sha1 hash / device token (hex WITH a letter; pure digits stay numbers)
	s/\d+/<N>/g;                                                                                         # any remaining number (phone, count, port)
'

# Tail of the pipeline: keep only the first TOP groups (0 = no limit). Uses awk
# rather than head so the whole input is consumed -- head would close the pipe
# early and make the upstream sort die with SIGPIPE (exit 141 under pipefail).
limit() {
	if [ "$TOP" -gt 0 ]; then awk -v n="$TOP" 'NR<=n'; else cat; fi
}

if [ "$BY_CLASS" -eq 1 ]; then
	# Group by component: strip the "[date] LEVEL: [" prefix and keep the class
	# name up to the closing "]".
	grep -hE "$record_re" "${matches[@]}" \
		| sed -E 's/^\[[^]]*\] [A-Z]+: \[([^]]*)\]:.*/\1/' \
		| sort | uniq -c | sort -rn | limit
else
	# Group by message: normalize the variable parts (see $normalize above) so
	# that records differing only in ids/numbers/dates collapse together.
	grep -hE "$record_re" "${matches[@]}" \
		| perl -pe "$normalize" \
		| sort | uniq -c | sort -rn | limit
fi
