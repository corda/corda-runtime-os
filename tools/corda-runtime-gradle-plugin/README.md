# Corda-Runtime-Gradle-Plugin

A Gradle plugin that wraps a subset of the SDK functions to facilitate their use in developer and CI scenarios.  
This supersedes the CSDE Gradle plugin.

Add the following extension properties

```groovy
    cordaRuntimeGradlePlugin {
        notaryVersion = "5.2.0.0"
        notaryCpiName = "NotaryServer"
        corDappCpiName = "MyCorDapp"
        cpiUploadTimeout = "30000"
        vnodeRegistrationTimeout = "60000"
        cordaProcessorTimeout = "300000"
        workflowsModuleName = "workflows"
        cordaClusterURL = "https://localhost:8888"
        cordaRestUser = "admin"
        cordaRestPasswd ="admin"
        composeFilePath = "config/combined-worker-compose.yml"
        networkConfigFile = "config/static-network-config.json"
        r3RootCertFile = "config/r3-ca-key.pem"
        skipTestsDuringBuildCpis = "false"
        // Only need to supply these if you want to use an unpublished version
        artifactoryUsername = findProperty('cordaArtifactoryUsername') ?: System.getenv('CORDA_ARTIFACTORY_USERNAME')
        artifactoryPassword = findProperty('cordaArtifactoryPassword') ?: System.getenv('CORDA_ARTIFACTORY_PASSWORD')
    }
```

In order to use the vNodesSetup functionality, you will have to provide the following files:

1. A docker compose yaml file, with the contents similar to

   a. For Kafka-enabled combined worker

```yaml
version: '2'
services:
  postgresql:
    image: postgres:14.10
    restart: unless-stopped
    tty: true
    environment:
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=password
      - POSTGRES_DB=cordacluster
    ports:
      - 5432:5432

  kafka:
    image: confluentinc/cp-kafka:latest
    ports:
      - 9092:9092
    environment:
      KAFKA_NODE_ID: 1
      CLUSTER_ID: ZDFiZmU3ODUyMzRiNGI3NG
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,DOCKER_INTERNAL://0.0.0.0:29092,CONTROLLER://0.0.0.0:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092,DOCKER_INTERNAL://kafka:29092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,DOCKER_INTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: DOCKER_INTERNAL
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_DEFAULT_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"

  kafka-create-topics:
    image: openjdk:17-jdk
    depends_on:
      - kafka
    volumes:
      - ${CORDA_CLI:-~/.corda/cli}:/opt/corda-cli
    working_dir: /opt/corda-cli
    command: [ "java",
               "-jar",
               "corda-cli.jar",
               "topic",
               "-b=kafka:29092",
               "create",
               "connect"
    ]

  corda:
    image: corda-os-docker.software.r3.com/corda-os-combined-worker-kafka:5.2.0.0
    depends_on:
      - postgresql
      - kafka
      - kafka-create-topics
    command: [ "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005",
               "-mbus.busType=KAFKA",
               "-mbootstrap.servers=kafka:29092",
               "-spassphrase=password",
               "-ssalt=salt",
               "-ddatabase.user=user",
               "-ddatabase.pass=password",
               "-ddatabase.jdbc.url=jdbc:postgresql://postgresql:5432/cordacluster",
               "-ddatabase.jdbc.directory=/opt/jdbc-driver/"
    ]
    ports:
      - 8888:8888
      - 7004:7004
      - 5005:5005
```

NOTE: The above docker compose yaml file:
 - Uses the Kafka-enabled Combined Worker image
 - Properties `bus.busType=KAFKA` and `bootstrap.servers=kafka:29092` are used to configure the Combined Worker to use Kafka
 - Runs Kafka in KRaft mode 
 - To ensure that the Combined Worker starts correctly, corda cli needs to be used to create necessary topics. Corda cli version should be aligned with the Combined Worker version


   b. For Database-only combined worker

```yaml
version: '2'
services:
  postgresql:
    image: postgres:14.10
    restart: unless-stopped
    tty: true
    environment:
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=password
      - POSTGRES_DB=cordacluster
    ports:
      - 5432:5432

  corda:
    image: corda-os-docker.software.r3.com/corda-os-combined-worker:5.2.0.0
    depends_on:
      - postgresql
    command: [ "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005",
               "-mbus.busType=DATABASE",
               "-spassphrase=password",
               "-ssalt=salt",
               "-ddatabase.user=user",
               "-ddatabase.pass=password",
               "-ddatabase.jdbc.url=jdbc:postgresql://postgresql:5432/cordacluster",
               "-ddatabase.jdbc.directory=/opt/jdbc-driver/"
    ]
    ports:
      - 8888:8888
      - 7004:7004
      - 5005:5005
```
Ensure the port given in the compose file matches the cordaClusterURL setting in the cordaRuntimeGradlePlugin extension properties; default is 8888.

2. [Gradle plugin default signing key](https://github.com/corda/corda-runtime-os/wiki/CorDapp-Packaging#trust-the-gradle-plugin-default-signing-key)

3. [R3 signing key](https://github.com/corda/corda-runtime-os/wiki/CorDapp-Packaging#trust-the-r3-signing-key)

4. A config json file representing the nodes on the network, with the contents similar to

```json
[
  {
    "x500Name" : "CN=Alice, OU=Test Dept, O=R3, L=London, C=GB",
    "cpi" : "MyCorDapp"
  },
  {
    "x500Name" : "CN=Bob, OU=Test Dept, O=R3, L=London, C=GB",
    "cpi" : "MyCorDapp"
  },
  {
    "x500Name" : "CN=Charlie, OU=Test Dept, O=R3, L=London, C=GB",
    "cpi" : "MyCorDapp"
  },
  {
    "x500Name" : "CN=NotaryRep1, OU=Test Dept, O=R3, L=London, C=GB",
    "cpi" : "NotaryServer",
    "serviceX500Name": "CN=NotaryService, OU=Test Dept, O=R3, L=London, C=GB"
  }
]

```