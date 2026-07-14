#!/bin/bash
export JAVA_HOME=/home/traceback/jdk-21.0.2
export PATH=$JAVA_HOME/bin:/home/traceback/apache-maven-3.9.6/bin:$PATH
cd /home/traceback/.openclaw/workspace/ecl-project/ecl-system/ecl-bootstrap
exec mvn spring-boot:run >> /home/traceback/.openclaw/workspace/ecl-project/ecl-system/logs/backend.log 2>&1
