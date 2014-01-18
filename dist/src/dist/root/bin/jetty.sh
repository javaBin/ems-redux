#!/bin/bash

set -u
set -e
set -x

mkdir -p "$APP_HOME/logs"
exec >> "$APP_HOME/logs/ems.log"
exec 2>&1

CLASSPATH="$APP_HOME/lib/*:$APP_HOME/jetty.jar"

ARGS="-DAPP_HOME=$APP_HOME -cp $CLASSPATH jetty.Jetty"

# Plainly pass any argument in the "jetty" group to EMS.
for line in $(app cat-conf -g jetty | cut -f 2- -d .)
do
  ARGS="$ARGS -D$line"
done
exec java ${ARGS} \
   2>&1
