## Deploying Kafka clusters locally using Docker and running the Kafka integration tests

You can use the scripts included in this folder to perform local deployments of full-blown Kafka clusters using Docker.

There are 2 environments you can deploy:
* an environment containing 1 Kafka cluster (3 brokers) connected to 1 Zookeeper cluster (3 servers) suitable for testing one node.
* an environment containing 2 Kafka clusters (3 brokers each) connected to 1 Zookeeper cluster each (3 servers each) suitable for testing two nodes.

In both cases, an admin UI is also deployed (available at `localhost:8080`) that you can use to inspect basic information about the Kafka clusters.

You can find the ports the Kafka brokers are listening to (for connections from clients) using `docker ps`.

### Deploying the environment

You can deploy the 1-cluster environment using the following command:
```
docker-compose -f single-kafka-cluster.yml up -d
```

You can deploy the 2-cluster environment using the following command:
```
docker-compose -f two-kafka-clusters.yml up -d
```

On Linux, you will have to give the full read/write permission to all the users to the `data` directory, or run
```
docker-compose -f linux-friendly-kafka-cluster.yml up -d
```
which will not persist the data under the `data` directory.

### Running the Kafka Integration Tests

To run the tests
```
gradlew clean kafkaIntegrationTest
```

### Tearing down the environment

You can stop the environment using Ctrl+C and clean up the containers using:
```
docker-compose -f single-kafka-cluster.yml down
```

The persistent data of all the servers are stored under the `data` folder, so it's good to delete this folder too when tearing down an environment.
