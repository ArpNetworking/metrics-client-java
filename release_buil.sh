#!/bin/bash

xmllint --xpath "/*[local-name()='project']/*[local-name()='version'][1]/text()" pom.xml | grep '\-SNAPSHOT'
retval=$?
if [ $retval == 0 ]; then
  ./mvnw -DskipDeploy=false clean deploy -U
else
  ./mvnw -DskipDeploy=false -DskipRollerUpload=false clean deploy -U
fi

