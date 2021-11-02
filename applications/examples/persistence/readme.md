# Persistence Demo App

## Introduction

The purpose of this demo app is to show the `db-admin` and `db-orm` libraries working in an OSGi app,
as described in https://r3-cev.atlassian.net/browse/CORE-2441.

The example used is "Cluster Configuration", and follow the first 2 flows as described in:
https://lucid.app/lucidspark/invitations/accept/inv_3879490b-d04e-495b-87c7-5322ca73dd42

NOTE: while this example was chosen as to trigger some thinking about cluster admin, its purpose is _not_
to be an example of how cluster configuration will work eventually.

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
docker compose up
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

This app will start 2 subscriptions:

* Cluster Admin Events: messages on this topic simply trigger a DB schema migration for the cluster_config table.
* Config Admin Events: messages on this topic trigger creation or update of a row in the cluster_config table.

## Persistence Demo Publisher

There is another, small, CLI to publish messages on Kafka that are needed to drive the demo.

To compile the app:

From `applications/examples/persistence/persistence-demo-publisher`

```shell
gradle clean assemble
```

### Cluster Admin Message

Publish a Cluster Admin message like so:

```shell
java -jar build/bin/corda-persistence-demo-publisher-5.0.0.0-SNAPSHOT.jar cluster-admin --kafka localhost:9093
```

### Config Admin Message

Publish a Config Create/Update message like so:

```shell
java -jar build/bin/corda-persistence-demo-publisher-5.0.0.0-SNAPSHOT.jar config-admin --kafka localhost:9093 -c [key] -d [value] -e [version]
```

Where `[key]` is the config key `[value]` is the config value and `[version]` is an optional config
version. If not supplied, the version will default to 1.

After that it should be possible to connect to PosgreSQL using:
```
Host: localhost
Port: 5433
Database: cordacluster
Username: user
Password: password
```

And see that `cluster_config` table indeed has desired `key` and `value` pair.