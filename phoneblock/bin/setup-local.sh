#!/bin/bash
#
# One-time setup of a fresh workspace for running the PhoneBlock web app locally.
#
# Creates the two git-ignored local config files from their checked-in templates:
#   - phoneblock/src/test/jetty/jetty-env.xml   (JNDI: local H2 DB, test-only SMTP)
#   - phoneblock/.phoneblock                    (required by the build's enforcer rule)
#
# Existing files are never overwritten, so the script is safe to re-run.

set -e

# Resolve the phoneblock module directory independently of the caller's CWD.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$(dirname "$SCRIPT_DIR")"

create_from_template() {
	local template="$1"
	local target="$2"
	if [ -e "$target" ]; then
		echo "  exists, kept: $target"
	elif [ ! -e "$template" ]; then
		echo "  ERROR: template missing: $template" >&2
		return 1
	else
		cp "$template" "$target"
		echo "  created:      $target"
	fi
}

echo "Setting up local PhoneBlock config:"
create_from_template \
	"$MODULE_DIR/src/test/jetty/jetty-env.template.xml" \
	"$MODULE_DIR/src/test/jetty/jetty-env.xml"
create_from_template \
	"$MODULE_DIR/.phoneblock.template" \
	"$MODULE_DIR/.phoneblock"

cat <<EOF

Done. Start the server with:

  cd "$MODULE_DIR"
  mvn jetty:run

Then open http://localhost:8080/phoneblock/

The translation plugins (DeepL) live in the 'with-deepl' profile, so a regular
build needs no API key. Re-generate translations explicitly with:

  mvn -Pwith-deepl ...   # see the with-deepl profile comment in phoneblock/pom.xml

To exercise the donation-credits / IMAP feature, fill in the commented-out
imap/* and credits/* entries in jetty-env.xml with real credentials. Keep those
out of git: jetty-env.xml is git-ignored for exactly this reason.
EOF
