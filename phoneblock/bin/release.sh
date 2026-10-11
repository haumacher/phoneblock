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

(
    cd phoneblock_answerbot_ui/
    flutter build web --base-href /ab/
)

mvn -B clean release:prepare
mvn release:clean

echo
echo "Tag pushed. Follow the build at https://github.com/haumacher/phoneblock/actions, then deploy with"
echo "  ssh phoneblock.net sudo pb-deploy <version> pb-test"
