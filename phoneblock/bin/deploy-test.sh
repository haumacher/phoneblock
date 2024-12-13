#!/bin/bash

set -ex

( 
    cd phoneblock_answerbot_ui/ 
    flutter build web --base-href /pb-test/ab/ 
)

mvn install && scp phoneblock/target/phoneblock-1.5.0-SNAPSHOT.war phoneblock.net:/var/lib/tomcat10/webapps/pb-test.war

