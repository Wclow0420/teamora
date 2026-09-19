#!/usr/bin/env bash
#
# Run the backend test suite (no local Java/Maven needed). Spins the tests in a
# Maven container on the Compose network, against a disposable `teamora_test`
# database on the running Postgres — so dev data is never touched.
#
# Usage:  ./scripts/test-backend.sh
#
set -euo pipefail

cd "$(dirname "$0")/.."   # → backend/

PROJECT="$(basename "$PWD")"          # compose project name (= "backend")
NETWORK="${PROJECT}_default"
M2_VOLUME="teamora-m2"                # cached Maven repo for fast re-runs

echo "▶ ensuring Postgres is up…"
docker compose up -d db >/dev/null

echo "▶ (re)creating teamora_test database…"
docker compose exec -T db psql -U teamora -d teamora -c "DROP DATABASE IF EXISTS teamora_test;" >/dev/null
docker compose exec -T db psql -U teamora -d teamora -c "CREATE DATABASE teamora_test;" >/dev/null

echo "▶ running mvn verify…"
docker run --rm \
  -v "$PWD":/app -w /app \
  -v "${M2_VOLUME}":/root/.m2 \
  --network "${NETWORK}" \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://db:5432/teamora_test" \
  -e SPRING_DATASOURCE_USERNAME=teamora \
  -e SPRING_DATASOURCE_PASSWORD=teamora \
  maven:3.9-eclipse-temurin-21 mvn -B verify

echo "✓ backend tests passed"
