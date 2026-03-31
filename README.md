implemented live on twitch: https://www.twitch.tv/anusikh

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
