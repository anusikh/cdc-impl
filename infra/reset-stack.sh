#!/usr/bin/env bash
# Reset Docker Compose stack for CDC demo by stopping containers and removing data volumes.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Stopping stack and removing volumes..."
docker compose -f "${HERE}/docker-compose.yml" down -v

echo "Ensuring named volumes are gone (idempotent)..."
docker volume rm -f infra_kafka_data infra_zookeeper_data postgres_data elasticsearch_data >/dev/null 2>&1 || true

echo "Starting stack fresh..."
docker compose -f "${HERE}/docker-compose.yml" up -d

echo "Done. Stack is up with clean volumes."
