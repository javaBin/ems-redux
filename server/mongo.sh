#!/bin/bash
MONGO=`which mongod`

if [ ! -d mongo ]; then
  mkdir mongo
fi

$MONGO --bind_ip 0.0.0.0 --journal --dbpath ./mongo
