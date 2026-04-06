#!/bin/sh
set -e

CONFIG_FILE="/connector.json"
CONNECT_URL="${CONNECT_URL:-http://connect:8083}"
CONNECTOR_NAME="${CONNECTOR_NAME:-postgres-connector}"
KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}"
DLQ_CONNECT_TOPIC="${DLQ_CONNECT_TOPIC:-cdc.dlq.connect}"
DLQ_APP_TOPIC="${DLQ_APP_TOPIC:-cdc.dlq.app}"
DLQ_PARTITIONS="${DLQ_PARTITIONS:-3}"
DLQ_REPLICATION_FACTOR="${DLQ_REPLICATION_FACTOR:-1}"
DLQ_RETENTION_MS="${DLQ_RETENTION_MS:-1209600000}"
DLQ_CLEANUP_POLICY="${DLQ_CLEANUP_POLICY:-compact,delete}"

find_kafka_topics_bin() {
  for candidate in \
    /kafka/bin/kafka-topics.sh \
    /opt/kafka/bin/kafka-topics.sh \
    /usr/bin/kafka-topics.sh \
    kafka-topics.sh
  do
    if [ -x "$candidate" ] || command -v "$candidate" >/dev/null 2>&1; then
      echo "$candidate"
      return 0
    fi
  done

  echo "Unable to find kafka-topics.sh" >&2
  exit 1
}

http_get_ok() {
  url="$1"

  if command -v curl >/dev/null 2>&1; then
    curl -sf "$url" >/dev/null 2>&1
    return $?
  fi

  if command -v wget >/dev/null 2>&1; then
    wget -q -O /dev/null "$url" >/dev/null 2>&1
    return $?
  fi

  echo "Neither curl nor wget is available for HTTP checks" >&2
  exit 1
}

register_connector() {
  if command -v curl >/dev/null 2>&1; then
    curl -sf -X PUT \
      -H "Content-Type: application/json" \
      --data @"${CONFIG_FILE}" \
      "${CONNECT_URL}/connectors/${CONNECTOR_NAME}/config"
    return 0
  fi

  if command -v wget >/dev/null 2>&1; then
    wget -qO- \
      --method=PUT \
      --header="Content-Type: application/json" \
      --body-file="${CONFIG_FILE}" \
      "${CONNECT_URL}/connectors/${CONNECTOR_NAME}/config" >/dev/null
    return 0
  fi

  echo "Neither curl nor wget is available for connector registration" >&2
  exit 1
}

wait_for_kafka() {
  echo "Waiting for Kafka at ${KAFKA_BOOTSTRAP_SERVERS}..."
  for i in $(seq 1 40); do
    if "$KAFKA_TOPICS_BIN" --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --list >/dev/null 2>&1; then
      return 0
    fi
    sleep 3
  done

  echo "Kafka not reachable at ${KAFKA_BOOTSTRAP_SERVERS} after waiting." >&2
  exit 1
}

wait_for_connect() {
  echo "Waiting for Kafka Connect at ${CONNECT_URL}..."
  for i in $(seq 1 40); do
    if http_get_ok "${CONNECT_URL}/connectors"; then
      return 0
    fi
    sleep 3
  done

  echo "Kafka Connect not reachable at ${CONNECT_URL} after waiting." >&2
  exit 1
}

create_topic() {
  topic_name="$1"

  echo "Ensuring topic ${topic_name} exists..."
  "$KAFKA_TOPICS_BIN" --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" \
    --create \
    --if-not-exists \
    --topic "$topic_name" \
    --partitions "${DLQ_PARTITIONS}" \
    --replication-factor "${DLQ_REPLICATION_FACTOR}" \
    --config cleanup.policy="${DLQ_CLEANUP_POLICY}" \
    --config retention.ms="${DLQ_RETENTION_MS}"

  "$KAFKA_TOPICS_BIN" --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" \
    --describe \
    --topic "$topic_name"
}

KAFKA_TOPICS_BIN="$(find_kafka_topics_bin)"

wait_for_kafka
create_topic "${DLQ_CONNECT_TOPIC}"
create_topic "${DLQ_APP_TOPIC}"

wait_for_connect

echo "Registering ${CONNECTOR_NAME} (idempotent)..."
register_connector

echo "Connector ${CONNECTOR_NAME} registered."
