#!/bin/sh
set -eu

# Secrets are mounted as files, never baked into the image or printed.
if [ -f /run/secrets/database_password ]; then
  DATABASE_PASSWORD=$(cat /run/secrets/database_password)
  export DATABASE_PASSWORD
fi
if [ -f /run/secrets/jwt_secret ]; then
  JWT_SECRET=$(cat /run/secrets/jwt_secret)
  export JWT_SECRET
fi
if [ -f /run/secrets/webhook_secret ]; then
  WEBHOOK_SECRET=$(cat /run/secrets/webhook_secret)
  export WEBHOOK_SECRET
fi
: "${DATABASE_PASSWORD:?Provide a database password}"
: "${JWT_SECRET:?Provide a JWT secret}"
exec java -jar /app/app.jar
