# Workers

This directory contains all worker types to be published.

In order to build the applications and publish the docker images use:

```shell
./gradlew publishOSGiImage
```

To start all workers, along with Kafka and the cluster DB, run the following from `applications/workers/release/deploy`:

```shell
docker compose up --remove-orphans
```

You may need to prune old containers if the configuration has changed:

```shell
docker container prune
```

For more details, please see [here](release/deploy/README.md).