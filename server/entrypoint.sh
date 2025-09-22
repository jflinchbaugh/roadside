#!/bin/sh

cd /app

java -server -Xms64m -Xmx64m -jar app.jar "${port}" "${dbhost}"

