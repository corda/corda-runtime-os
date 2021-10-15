# Persistence POC

## Design

https://lucid.app/lucidspark/invitations/accept/inv_3879490b-d04e-495b-87c7-5322ca73dd42

## Deploy/Run

Docker Compose configuration in the `deploy` folder. This sets up:
- Zookeeper
- Single Kafka broker
- Cluster DB (Postgres)
- App DB (Postgres)
- Kafdrop: UI for Kafka

### Start network:

In `applications/examples/persistence/deploy`:

```shell
docker-compose up
```

### Create Kafka topics:

When network is running, create topics:

```shell
./create-topics.sh
```

or to first delete existing persistence-demo topics:

```shell
./create-topics.sh delete
```

### Kafdrop

To launch the [Kafdrop](https://github.com/HomeAdvisor/Kafdrop) UI, browse to: http://localhost:9000/

## Demo App

Start the demo app (from `applications/examples/persistence/persistence-demo`):

```shell
gradle clean assemble 
java -jar build/bin/corda-persistence-demo-5.0.0.0-SNAPSHOT.jar --kafka localhost:9093 --jdbc-url jdbc:postgresql://localhost:5433/cordacluster
```

## Demo Publisher

There is another, small, CLI to publish messages on Kafka that are needed to drive the demo.

To compile the app:

```shell
gradle clean assemble
```

### Cluster Admin Message

```shell
java -jar persistence-demo-publisher/build/bin/corda-persistence-demo-publisher-5.0.0.0-SNAPSHOT.jar cluster-admin --kafka localhost:9093
```

### Config Admin Message

```shell
java -jar persistence-demo-publisher/build/bin/corda-persistence-demo-publisher-5.0.0.0-SNAPSHOT.jar config-admin --kafka localhost:9093
```