#!/bin/bash

set -ex

( 
    cd phoneblock_answerbot_ui/ 
    flutter build web --base-href /pb-test/ab/ 
)

mvn clean install

scp phoneblock/target/phoneblock-*.war phoneblock.net:/var/lib/tomcat10/webapps/pb-test.war

