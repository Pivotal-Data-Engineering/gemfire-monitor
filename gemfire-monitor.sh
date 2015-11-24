#!/bin/bash

if [[ "${JAVA_HOME}x" == "x" ]]
then
  echo PLEASE SET JAVA_HOME
  exit 1
fi

HERE=`dirname $0`
CLASSPATH=$HERE:$HERE/target/'*'

$JAVA_HOME/bin/java -cp "$CLASSPATH" io.pivotal.pde.gemfire.monitor.GemFireMonitor $* 

