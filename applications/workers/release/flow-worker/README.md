# Running the flow worker

from root project dir:

```shell
gradlew clean applications:flow-worker:appJar testing:cpbs:helloworld:build`
```

from flow-worker app dir:
```shell
java -jar corda-flow-worker-5.0.0.0-SNAPSHOT.jar --instanceId 1
```

configure cpb dir (when using the `LocalPackageCache` impl)

```shell
java -jar corda-flow-worker-5.0.0.0-SNAPSHOT.jar --instanceId 1 \
    --additionalParams config.topic.name="config.topic" \
    --additionalParams corda.cpi.cacheDir="C:\dev\corda-runtime-os\testing\cpbs\helloworld\build\libs"
```

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

```shell
cd applications\workers\release\flow-worker
java -jar build/bin/corda-flow-worker-5.0.0.0-SNAPSHOT.jar  --instanceId 1 \
    --additionalParams config.topic.name="config.topic" \
    --additionalParams corda.cpi.cacheDir="C:\dev\corda-runtime-os\testing\cpbs\helloworld\build\libs"
```

You may find it helpful to in Intellij to create a "JAR Application configuration" to run this step, and additionally
make that configuration run the gradle `appJar` task (above), as necessary.