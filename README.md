## quick start (auto connector registration)

That script:
- runs `docker compose -f infra/docker-compose.yml up -d`
- waits for Kafka and Kafka Connect
- creates DLQ topics
- idempotently PUTs the `postgres-connector` config from `infra/connector-configs/postgres-connector.json`

```bash
# create consumer to check if connect is sending CDC events
docker exec -it infra-kafka-1 /kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server infra-kafka-1:9092 \
  --topic dbserver1.public.users \
  --from-beginning

# inspect the connect DLQ
docker exec -it infra-kafka-1 /kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server infra-kafka-1:9092 \
  --topic cdc.dlq.connect \
  --from-beginning

# inspect the app DLQ
docker exec -it infra-kafka-1 /kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server infra-kafka-1:9092 \
  --topic cdc.dlq.app \
  --from-beginning

# insert into table
docker exec -it postgres-cdc psql -U postgres -d postgres -c \
  "INSERT INTO users (full_name, email) VALUES ('Test User', 'test@example.com');"

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

# get config
curl http://localhost:8083/connectors/postgres-connector/config

## verify app DLQ

Use this to verify the Spring consumer sends malformed records to `cdc.dlq.app`.

1. start the infra stack

```bash
docker compose -f infra/docker-compose.yml up -d
```

2. start the sink app

```bash
cd cdcink
./mvnw spring-boot:run
```

3. publish a malformed Debezium-style event to the source topic
   - this payload is intentionally missing `payload.source.db` and `payload.source.table`
   - `DebeziumKafkaConsumer.validateEnvelope(...)` should reject it

```bash
docker exec -i infra-kafka-1 /kafka/bin/kafka-console-producer.sh \
  --bootstrap-server kafka:9092 \
  --topic dbserver1.public.users \
  --property parse.key=true \
  --property key.separator='|' <<'EOF'
readme-check-key|{"payload":{"op":"c","ts_ms":1712419201000,"after":{"id":2001,"full_name":"README Check","email":"readme@test.local"},"source":{"schema":"public"}}}
EOF
```

4. read the app DLQ with headers enabled

```bash
docker exec infra-kafka-1 /kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 \
  --topic cdc.dlq.app \
  --from-beginning \
  --max-messages 5 \
  --timeout-ms 5000 \
  --property print.key=true \
  --property print.headers=true
```

Expected result:
- the record appears on `cdc.dlq.app`
- the key is preserved
- Spring adds `kafka_dlt_*` headers
- the app adds:
  - `dlq.source=app`
  - `dlq.error=com.anusikh.cdcink.service.exception.NonRetryableCdcException`
  - `dlq.error-message=Missing Debezium source metadata for topic dbserver1.public.users`
  - `dlq.original-topic=dbserver1.public.users`
