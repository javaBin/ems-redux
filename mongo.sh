#!/bin/bash
MONGO=`which mongod`
#MONGO=/Users/maedhros/Downloads/mongodb-osx-x86_64-2.0.4/bin/mongod
if [ ! -d mongo ]; then
  mkdir mongo
fi

$MONGO --bind_ip 0.0.0.0 --journal --dbpath ./mongo
