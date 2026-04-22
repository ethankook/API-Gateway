#!/usr/bin/env bash

# You have to chmod +x this file 
echo "Starting Orchard services..."

if [ -f .env ]; then
  set -a
  . ./.env
  set +a
fi

java -jar gateway/target/gateway-0.0.1-SNAPSHOT.jar &
java -jar scheduler/target/scheduler-0.0.1-SNAPSHOT.jar &
