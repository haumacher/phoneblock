#!/bin/bash
#
# Cut a server release.
#
# Bumps the version, tags the release and pushes the tag. GitHub Actions
# (.github/workflows/server-release.yml) then builds the WAR and attaches it to the GitHub
# release; the answerbot Docker image is published by answerbot-docker.yml at the same time.
#
# Deploy afterwards on the server (test first, then production):
#
#   ssh phoneblock.net sudo pb-deploy <version> pb-test
#   ssh phoneblock.net sudo pb-deploy <version> phoneblock
#
# release:prepare verifies the build locally, which needs the answerbot UI built once.

set -e

cd "$(dirname "$0")/../.."

# The version being released: the current snapshot without its suffix.
VERSION=$(mvn -q -N help:evaluate -Dexpression=project.version -DforceStdout | sed 's/-SNAPSHOT$//')
NOTES="release-notes/$VERSION.md"
if [ ! -f "$NOTES" ]; then
    echo "Missing release notes $NOTES - write them first (see release-notes/README.md)." >&2
    exit 1
fi

# Stamp the release date into the notes (they start as "*unreleased*") and commit it, so that
# release:prepare runs on a clean tree and the tag carries the dated notes.
if grep -q '^\*unreleased\*' "$NOTES"; then
    sed -i "s/^\*unreleased\*/*$(date +%Y-%m-%d)*/" "$NOTES"
    git add "$NOTES"
    git commit -m "Release notes $VERSION: set the release date"
fi

(
    cd phoneblock_answerbot_ui/
    flutter build web --base-href /ab/
)

mvn -B clean release:prepare
mvn release:clean

echo
echo "Tag pushed. Follow the build at https://github.com/haumacher/phoneblock/actions, then deploy with"
echo "  ssh -t phoneblock.net sudo pb-deploy <version> pb-test"
