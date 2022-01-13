# Running the flow worker

## In depth instructions

###  Compile flow worker using `appJar` task and cpb

```shell
gradlew applications:workers:release:flow-worker:appJar applications:tools:flow-worker-setup:appJar testing:cpbs:helloworld:build
```

### Start Docker Compose

```shell
cd  testing\message-patterns\kafka-docker
docker-compose -f single-kafka-cluster.yml up -d
```

To tear it down, in the same folder, run:

```shell
docker-compose -f single-kafka-cluster.yml down
```

### Run Kafka Setup

```shell
cd applications\tools\flow-worker-setup
java -jar build/bin/corda-flow-worker-setup-5.0.0.0-SNAPSHOT.jar --config config.conf --topic topics.conf --instanceId=1 
```

### Run Test App
There are two options for passing the CPK Cache directory to the worker, either it can be published on the configuration kafka topic
by the setup app (see the application/tools/flow-worker-setpup/config.conf as an example)
or it can be specified as an optional param on the command line (see example below)

```shell
cd applications\workers\release\flow-worker
java -jar build/bin/corda-flow-worker-5.0.0.0-SNAPSHOT.jar  --instanceId 1 \
--additionalParams corda.cpi.cacheDir="./../../../testing/cpbs/helloworld/build/libs"
```

You may find it helpful to in Intellij to create a "JAR Application configuration" to run this step, and additionally
make that configuration run the gradle `appJar` task (above), as necessary.