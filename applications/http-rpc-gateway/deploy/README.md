# HTTP RPC Gateway Application Docker Compose

The purpose of this Docker Compose cluster to run: 
- HTTP RPC Gateway;
- A prototype of DB Worker;
- Kafka Bus;
- Postgres SQL server;

locally to be able to demonstrate HTTP RPC functionality built to date including new way of RPC permissions.

## Preparing for run

In order to get started it is necessary to:
- Start Docker Desktop.
- Build an image of HTTP RPC Gateway, please see [here](../README.md#building-the-docker-image).
- TODO: Explain how to build DBWorker prototype.

## Start the network

In `applications/http-rpc-gateway/deploy`:

```shell
docker compose -p "http-rpc" up
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

### Swagger UI

To launch Swagger UI and execute HTTP RPC calls please browse to: https://localhost:8888/api/v1/swagger