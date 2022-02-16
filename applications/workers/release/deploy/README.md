# All the workers Docker Compose

The purpose of this Docker Compose cluster to run: 
- RPC Worker;
- DB Worker;
- Kafka Bus;
- Postgres SQL server;
- Flow Worker;
- Crypto Worker;
- Member Worker.

locally to be able to demonstrate functionality built to date.

## Preparing for run

In order to get started it is necessary to:
- Start Docker Desktop.
- Build the images of the workers:
```
gradlew :applications:workers:release:crypto-worker:publishOSGiImage :applications:workers:release:db-worker:publishOSGiImage :applications:workers:release:flow-worker:publishOSGiImage :applications:workers:release:rpc-worker:publishOSGiImage :applications:workers:release:member-worker:publishOSGiImage
```

## Start the network

In this folder run:

```shell
docker compose -p "All-Workers" up
```

### Verify everything works

Once the plant is started it should be possible to run E2E tests like so:

`gradlew :applications:workers:release:rpc-worker:e2eTest`

For more information on E2E tests, please see [here](../rpc-worker/src/e2eTest/README.md). 

### View Kafka topics content

Kafka topics are created automatically by Docker Compose.

To launch the [Kafdrop](https://github.com/HomeAdvisor/Kafdrop) UI, browse to: http://localhost:9000/

### Swagger UI

To launch Swagger UI and execute HTTP RPC calls please browse to: https://localhost:8888/api/v1/swagger

### Viewing DB Content

Using your favourite DB editor (like [DBeaver](https://dbeaver.io/)) it is possible to connect to:
`user=user`
`password=pass`
`jdbc.url=jdbc:postgresql://cluster-db:5432/cordacluster`

There should be two schemas with DB objects: `config` and `rpc_rbac`.

### Debugging

Workers expose debug ports as follows:

| Worker Name   | Debug Port  |
| ------------- | ----------- |
| RPC Worker    | 5005        |
| DB Worker     | 5006        |
| Crypto Worker | 5007        |
| Flow Worker   | 5008        |
| Member Worker | 5009        |

Remote debugger can be used against: `localhost:<port>`.