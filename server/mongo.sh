#!/bin/bash
MONGO=`which mongod`

if [ ! -d mongo ]; then
  mkdir mongo
fi

$MONGO --journal --dbpath ./mongo