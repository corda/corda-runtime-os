# HTTP RPC Gateway Application Docker Compose

The purpose of this Docker Compose cluster to run: 
- HTTP RPC Gateway;
- A DB Worker;
- Kafka Bus;
- Postgres SQL server;

locally to be able to demonstrate HTTP RPC functionality built to date including new way of RPC permissions.

## Preparing for run

In order to get started it is necessary to:
- Start Docker Desktop.
- Build an image of HTTP RPC Gateway, please see [here](../README.md#building-the-docker-image).
- Build an image of DB Worker using: 
```
gradlew :applications:workers:release:db-worker:publishOSGiImage
```

## Start the network

In `applications/http-rpc-gateway/deploy`:

```shell
docker compose -p "http-rpc" up
```

### Kafdrop

To launch the [Kafdrop](https://github.com/HomeAdvisor/Kafdrop) UI, browse to: http://localhost:9000/

### Swagger UI

To launch Swagger UI and execute HTTP RPC calls please browse to: https://localhost:8888/api/v1/swagger

### Manipulate Kafka topics

Kafka topics are created automatically by Docker Compose.

If you do, however, need to manipulate Kafka topic, there is a dedicated script for that.

To create topics:
```shell
./create-topics.sh
```

or to first delete existing topics and re-create them:

```shell
./create-topics.sh delete
```