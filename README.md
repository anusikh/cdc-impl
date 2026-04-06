implemented live on twitch: https://www.twitch.tv/anusikh

# CDC Dead Letter Queue (DLQ) Implementation Plan

This document is the end-to-end plan to introduce a Dead Letter Queue for the CDC → Kafka → Elasticsearch pipeline in this repo. The goal: no message is lost, poison records are isolated, and operators can replay after fixes.

## Current Topology (baseline)
- Source: Postgres 15 with WAL enabled (Debezium connector).
- Transport: Kafka (internal listener 9092, external 9094) plus Kafka Connect (Debezium 2.5 image).
- Sink: Spring Boot service (`cdcink`, Spring Kafka + Elasticsearch) indexing into Elasticsearch 7.17.

## Scope
- **In scope:** consumer-side DLQ for `cdcink`; Kafka Connect/Debezium DLQ; observability; replay tooling; integration tests; infra scripts to create topics.
- **Out of scope (for now):** schema registry, multi-cluster replication, security hardening (SASL/ACLs).

## Target DLQ design
- Primary topics: `dbserver1.public.*` (created by Debezium) are consumed by `cdcink`.
- DLQ topics (new):
  - `cdc.dlq.connect` – messages Connect cannot deserialize/convert (configured on connector).
  - `cdc.dlq.app` – messages the Spring consumer cannot index or validate.
- DLQ payload:
  - Original value and key (unchanged serialization).
  - Headers: `dlq.source` (`connect` | `app`), `dlq.error`, `dlq.stacktrace` (truncated), `dlq.timestamp`, `dlq.consumer-group`.
  - Partitioning: same key as source topic to preserve ordering on replay.

## Work plan
1) **Create DLQ topics in infra**
   - Add `kafka-topics` commands to `infra/connector-bootstrap.sh` or new script to create `cdc.dlq.connect` and `cdc.dlq.app` with 3 partitions, compaction + 14d retention (configurable).

2) **Kafka Connect / Debezium DLQ**
   - Update `infra/connector-configs/postgres-connector.json` with:
     - `errors.tolerance=all`
     - `errors.deadletterqueue.topic.name=cdc.dlq.connect`
     - `errors.deadletterqueue.context.headers.enable=true`
   - Keep `errors.deadletterqueue.topic.replication.factor=1` for local; allow override via env.

3) **Spring consumer DLQ (`cdcink`)**
   - Add producer factory + `KafkaTemplate` bean for DLQ publishing.
   - Configure `DefaultErrorHandler` with `DeadLetterPublishingRecoverer` targeting `cdc.dlq.app`, using exponential backoff (e.g., 1s → 30s, 3 attempts) and exception classification (non-retryable vs retryable).
   - Ensure `JsonDeserializer` is configured with `useTypeHeaders=false` and trusted packages already set; add explicit `typeMapper` if needed.
   - Add `@KafkaListener` processing that throws typed exceptions (validation, Elasticsearch errors) so handler can route correctly.
   - Add DLQ record enrichment (headers) before publishing.

4) **Replay path**
   - Create a small Spring Boot admin endpoint or CLI (`cdcink` command) to replay DLQ records back to the primary topic or to a shadow topic after fix; implement idempotent writes to ES using document id = `<topic>-<partition>-<offset>`.

5) **Observability**
   - Add Micrometer counters: `dlq.app.count`, `dlq.connect.count`, `dlq.replay.count`, plus timer for retry latency.
   - Log structured JSON for DLQ events (topic, partition, offset, errorClass, message).
   - Optional: Kibana saved search/dashboard for DLQ indices if mirrored to Elasticsearch.

6) **Configuration & ops**
   - Externalize DLQ topic names, retention, and backoff via `application.yml` and env (`CDC_DLQ_APP_TOPIC`, etc.).
   - Document local vs prod overrides (replication factor, compression, retention.ms, cleanup.policy).

7) **Testing**
   - Unit tests for error handler classification and header enrichment.
   - Integration test with embedded Kafka (or Testcontainers) to assert failed processing lands in `cdc.dlq.app`.
   - Smoke test path: insert bad payload in Postgres to trigger Debezium error and verify `cdc.dlq.connect` receives it.

8) **Rollout checklist**
   - Create topics, apply connector config, deploy updated `cdcink`.
   - Run integration suite; verify DLQ counters > 0 when injecting bad record.
   - Document replay SOP and add to runbook.

## Acceptance criteria
- Any deserialization/validation/indexing failure in `cdcink` is delivered to `cdc.dlq.app` with enriched headers and original payload.
- Kafka Connect pushes connector errors to `cdc.dlq.connect` without stopping the task.
- Replay tool can move messages from both DLQ topics back to primary topics idempotently.
- Metrics and logs expose DLQ volume and last error class.

## Next steps (implementation order)
1. Create topics + update connector config (infra).
2. Wire DLQ producer + `DefaultErrorHandler` in Spring app; add listener error classification.
3. Add tests (unit + embedded Kafka) and a minimal replay CLI/endpoint.
4. Add dashboards/runbook entries; test end-to-end with bad record injection.

---

## Legacy quick start (auto connector registration)

That script:
- runs `docker compose -f infra/docker-compose.yml up -d`
- waits for Kafka Connect on `localhost:8083`
- idempotently PUTs the `postgres-connector` config from `infra/connector-configs/postgres-connector.json`

```
# configure connect to listen to WAL
curl -i -X POST -H "Accept:application/json" -H "Content-Type:application/json" localhost:8083/connectors/ -d '{
"name": "postgres-connector",
"config": {
"connector.class": "io.debezium.connector.postgresql.PostgresConnector",
"tasks.max": "1",
"database.hostname": "postgres",
"database.port": "5432",
"database.user": "postgres",
"database.password": "postgres",
"database.dbname": "postgres",
"topic.prefix": "dbserver1",
"schema.include.list": "public",
"table.include.list": "public.users",
"plugin.name": "pgoutput"
}
}'

# create consumer to check if connect is sending CDC events
docker exec -it infra-kafka-1 /kafka/bin/kafka-console-consumer.sh \
--bootstrap-server infra-kafka-1:9092 \
--topic dbserver1.public.users \
--from-beginning

# insert into table
docker exec -it postgres-cdc psql -U postgres -d postgres -c "INSERT INTO users (full_name, email) VALUES ('Test User', 'test@example.com');"

# perform search
curl -X GET "localhost:9200/cdc_events/_search?pretty" -H 'Content-Type: application/json' -d'
{
"query": {
"match": {
"after.full_name": {
"query": "Test User",
"fuzziness": "AUTO"
}
}
}
}'
```
