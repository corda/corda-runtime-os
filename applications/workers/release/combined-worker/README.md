# Combined Worker

## Setup

### Postgres DB

Run postgres container in background:
```shell
docker run -d -p 5432:5432 --name postgresql -e POSTGRESQL_DATABASE=cordacluster -e POSTGRESQL_USERNAME=user -e POSTGRESQL_PASSWORD=password -e POSTGRESQL_POSTGRES_PASSWORD=password bitnami/postgresql:latest
```

Run it with logs visible in console and ensure the container is deleted (and data wiped) when the container is stopped:

```shell
docker run --rm -p 5432:5432 --name postgresql -e POSTGRESQL_DATABASE=cordacluster -e POSTGRESQL_USERNAME=user -e POSTGRESQL_PASSWORD=password -e POSTGRESQL_POSTGRES_PASSWORD=password bitnami/postgresql:latest
```

DB schema will be created automatically when worker is started.
TODO: DB bootstraping might change as CLI could be used instead

### Message Bus

#### Kafka

TODO: Database message bus should be used instead, this is just temporary work-around (which is the reason why all containers are not created together).
Note that topics were taken from k8s cluster and might be out of sync.

Create file `docker-compose.yml`:
```
---
version: '2'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:latest
    container_name: zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:latest
    container_name: kafka
    depends_on:
      - zookeeper
    ports:
      - 9092:9092
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_DEFAULT_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: true

```
Run:
```shell
docker compose up
```

Create file `create-topics.txt`:
```
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic certificates.rpc.ops  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic certificates.rpc.ops.resp  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic config.management  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic config.management.request  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic config.management.request.resp  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic config.topic --config "cleanup.policy=compact" --config "segment.ms=300000" --config "delete.retention.ms=300000" --config "min.compaction.lag.ms=60000" --config "max.compaction.lag.ms=300000" --config "min.cleanable.dirty.ratio=0.5" ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic crypto.event  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic crypto.config.hsm  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic crypto.config.hsm.label  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic crypto.config.member  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic crypto.key.info  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic crypto.key.soft  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic crypto.hsm.label  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic crypto.hsm.rpc.registration  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic crypto.hsm.rpc.registration.resp  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic crypto.hsm.rpc.configuration  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic crypto.hsm.rpc.configuration.resp  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic crypto.ops.flow  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic crypto.ops.rpc  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic crypto.ops.rpc.resp  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic crypto.ops.rpc.client  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic crypto.registration.hsm  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic flow.event  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic flow.event.state --config "cleanup.policy=compact" --config "segment.ms=300000" --config "delete.retention.ms=300000" --config "min.compaction.lag.ms=60000" --config "max.compaction.lag.ms=300000" --config "min.cleanable.dirty.ratio=0.5" ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic flow.event.dlq  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic flow.mapper.event  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic flow.mapper.event.state --config "cleanup.policy=compact" --config "segment.ms=300000" --config "delete.retention.ms=300000" --config "min.compaction.lag.ms=60000" --config "max.compaction.lag.ms=300000" --config "min.cleanable.dirty.ratio=0.5" ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic flow.mapper.event.dlq  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic flow.status --config "cleanup.policy=compact" --config "segment.ms=300000" --config "delete.retention.ms=300000" --config "min.compaction.lag.ms=60000" --config "max.compaction.lag.ms=300000" --config "min.cleanable.dirty.ratio=0.5" ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic membership.event --config "cleanup.policy=compact" --config "segment.ms=300000" --config "delete.retention.ms=300000" --config "min.compaction.lag.ms=60000" --config "max.compaction.lag.ms=300000" --config "min.cleanable.dirty.ratio=0.5" ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic membership.group.cpi.whitelists  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic membership.group.params  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic membership.proposals  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic membership.update  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic membership.members --config "cleanup.policy=compact" --config "segment.ms=300000" --config "delete.retention.ms=300000" --config "min.compaction.lag.ms=60000" --config "max.compaction.lag.ms=300000" --config "min.cleanable.dirty.ratio=0.5" ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic membership.rpc.ops  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic membership.rpc.ops.resp  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic membership.ops.rpc  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic membership.ops.rpc.resp  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic p2p.in  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic p2p.out  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic p2p.out.markers  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic p2p.out.markers.state --config "cleanup.policy=compact" --config "segment.ms=300000" --config "delete.retention.ms=300000" --config "min.compaction.lag.ms=60000" --config "max.compaction.lag.ms=300000" --config "min.cleanable.dirty.ratio=0.5" ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic p2p.out.markers.dlq  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic p2p.hosted.identities --config "cleanup.policy=compact" --config "segment.ms=300000" --config "delete.retention.ms=300000" --config "min.compaction.lag.ms=60000" --config "max.compaction.lag.ms=300000" --config "min.cleanable.dirty.ratio=0.5" ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic link.in  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic link.out  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic gateway.tls.certs --config "cleanup.policy=compact" --config "segment.ms=300000" --config "delete.retention.ms=300000" --config "min.compaction.lag.ms=60000" --config "max.compaction.lag.ms=300000" --config "min.cleanable.dirty.ratio=0.5" ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic gateway.tls.truststores --config "cleanup.policy=compact" --config "segment.ms=300000" --config "delete.retention.ms=300000" --config "min.compaction.lag.ms=60000" --config "max.compaction.lag.ms=300000" --config "min.cleanable.dirty.ratio=0.5" ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic session.out.partitions --config "cleanup.policy=compact" --config "segment.ms=300000" --config "delete.retention.ms=300000" --config "min.compaction.lag.ms=60000" --config "max.compaction.lag.ms=300000" --config "min.cleanable.dirty.ratio=0.5" ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic p2p.members.info --config "cleanup.policy=compact" --config "segment.ms=300000" --config "delete.retention.ms=300000" --config "min.compaction.lag.ms=60000" --config "max.compaction.lag.ms=300000" --config "min.cleanable.dirty.ratio=0.5" ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic p2p.group.policies --config "cleanup.policy=compact" --config "segment.ms=300000" --config "delete.retention.ms=300000" --config "min.compaction.lag.ms=60000" --config "max.compaction.lag.ms=300000" --config "min.cleanable.dirty.ratio=0.5" ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic p2p.crypto.keys --config "cleanup.policy=compact" --config "segment.ms=300000" --config "delete.retention.ms=300000" --config "min.compaction.lag.ms=60000" --config "max.compaction.lag.ms=300000" --config "min.cleanable.dirty.ratio=0.5" ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic user.permissions.management  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic permissions.user.summary --config "cleanup.policy=compact" --config "segment.ms=300000" --config "delete.retention.ms=300000" --config "min.compaction.lag.ms=60000" --config "max.compaction.lag.ms=300000" --config "min.cleanable.dirty.ratio=0.5" ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic rpc.permissions.management  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic rpc.permissions.management.resp  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic rpc.permissions.group --config "cleanup.policy=compact" --config "segment.ms=300000" --config "delete.retention.ms=300000" --config "min.compaction.lag.ms=60000" --config "max.compaction.lag.ms=300000" --config "min.cleanable.dirty.ratio=0.5" ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic rpc.permissions.permission --config "cleanup.policy=compact" --config "segment.ms=300000" --config "delete.retention.ms=300000" --config "min.compaction.lag.ms=60000" --config "max.compaction.lag.ms=300000" --config "min.cleanable.dirty.ratio=0.5" ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic rpc.permissions.user --config "cleanup.policy=compact" --config "segment.ms=300000" --config "delete.retention.ms=300000" --config "min.compaction.lag.ms=60000" --config "max.compaction.lag.ms=300000" --config "min.cleanable.dirty.ratio=0.5" ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic rpc.permissions.role --config "cleanup.policy=compact" --config "segment.ms=300000" --config "delete.retention.ms=300000" --config "min.compaction.lag.ms=60000" --config "max.compaction.lag.ms=300000" --config "min.cleanable.dirty.ratio=0.5" ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic cpi.info --config "cleanup.policy=compact" --config "segment.ms=300000" --config "delete.retention.ms=300000" --config "min.compaction.lag.ms=60000" --config "max.compaction.lag.ms=300000" --config "min.cleanable.dirty.ratio=0.5" ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic cpi.upload  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic cpi.chunk.writer  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic cpi.upload.status  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic cpk.file --config "cleanup.policy=compact" --config "segment.ms=300000" --config "delete.retention.ms=300000" --config "min.compaction.lag.ms=60000" --config "max.compaction.lag.ms=300000" --config "min.cleanable.dirty.ratio=0.5" ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic db.entity.processor  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic virtual.node.entity.processor  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic virtual.node.management  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic virtual.node.creation.request  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic virtual.node.creation.request.resp  ;
kafka-topics --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1 --create --if-not-exists --topic virtual.node.info --config "cleanup.policy=compact" --config "segment.ms=300000" --config "delete.retention.ms=300000" --config "min.compaction.lag.ms=60000" --config "max.compaction.lag.ms=300000" --config "min.cleanable.dirty.ratio=0.5" ;
```

Run:
```shell
docker exec -i kafka /bin/bash < create-topics.txt
```

## Interact with the worker

The worker will expose the HTTP API on port 8888: https://localhost:8888/api/v1/swagger 
The status endpoint is also exposed: http://localhost:7000/status

## Smoketests

Run the [smoketests](/applications/workers/workers-smoketest/) to validate the combined worker.

Note that some tests require an empty environment (e.g. CPI upload).