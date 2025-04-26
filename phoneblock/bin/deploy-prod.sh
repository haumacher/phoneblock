#!/bin/bash

set -ex

( 
    cd phoneblock_answerbot_ui/ 
    flutter build web --base-href /phoneblock/ab/ 
)

mvn clean release:prepare -B

scp phoneblock/target/phoneblock-*.war phoneblock.net:/var/lib/tomcat10/webapps/phoneblock.war

mvn release:clean

