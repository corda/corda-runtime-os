# Workers

This directory contains all worker types to be published.

In order to build the applications and publish the docker images use:

```shell
./gradlew publishOSGiImage
```

To start all workers, along with Kafka and the cluster DB please follow the instructions
[here](https://github.com/corda/corda-runtime-os/wiki/Local-development-with-Kubernetes).