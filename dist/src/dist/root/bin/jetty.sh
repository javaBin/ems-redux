#!/bin/bash

mkdir -p "$APP_HOME/logs"
exec >> "$APP_HOME/logs/ems.out"
exec 2>&1

CLASSPATH="$APP_HOME/current/lib/*:$APP_HOME/current/jetty.jar"

ARGS="-cp $CLASSPATH -DEMS_HOME=$APP_HOME/current -DAPP_HOME=$APP_HOME"

echo "Starting jetty with classpath: $CLASSPATH"

# Plainly pass any argument in the "jetty" group to EMS.
for line in $(app cat-conf -g jetty | cut -f 2- -d .)
do
  ARGS="$ARGS -D$line"
done
echo "Running java ${ARGS} jetty.Jetty"
exec java ${ARGS} jetty.Jetty \
   2>&1
