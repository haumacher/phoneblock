#!/bin/bash
#
# Deploy the current working tree to the pb-test context on phoneblock.net.
#
# Builds the answerbot UI and the WAR locally, copies the WAR to the server and hands it to
# pb-deploy, which stops the pb-test context through the Tomcat manager, backs up its database
# and the current WAR, exchanges the WAR, waits for the redeployment and then follows the log
# (Ctrl-C ends it). Production keeps running.
#
# Releases are deployed from the GitHub release instead: ssh phoneblock.net sudo pb-deploy <version> pb-test

set -e

cd "$(dirname "$0")/../.."

(
    cd phoneblock_answerbot_ui/
    flutter build web --base-href /ab/
)

mvn -B clean install

WAR=$(ls phoneblock/target/phoneblock-*.war)
NAME=$(basename "$WAR")

scp "$WAR" "phoneblock.net:/tmp/$NAME"
ssh -t phoneblock.net "sudo pb-deploy --war /tmp/$NAME pb-test && rm -f /tmp/$NAME"
