# Flow Worker Deployment Steps

## Step 1 - Build
Build and publish the necessary modules. These will be the flow-worker itself, the setup tool and the helloworld cordapp
`gradlew clean applications:workers:release:flow-worker:publishOSGiImage applications:tools:flow-worker-setup:appJar testing:cpbs:helloworld:build`

## Step 2 - Compose
Running the docker-compose script
`docker compose -p "Flow-worker" up`
This script contains 1 zookeeper instance, 1 kafka broker, the kafka UI (available at `http://localhost:8081/`) and 3 instances
of flow-worker all connected to the same broker

##Step 3 - Configure
Pushing the required configuration onto the kafka topics. This is done using the flow-worker-setup tool
`java -jar ../flow-worker/applications/tools/flow-worker-setup/build/bin/corda-flow-worker-setup-5.0.0.0-SNAPSHOT.jar PublishConfig SetupVirtualNode --cpiDir ../flow-worker/testing/cpbs/helloworld/build/libs --cpiDockerDir /cpiDir  --config ../flow-worker/applications/tools/flow-worker-setup/config.conf`

##Step 4 - StartFlow
Starting a flow is done through the flow-worker-setup tool by just running its StartFlow task
`java -jar ../flow-worker/applications/tools/flow-worker-setup/build/bin/corda-flow-worker-setup-5.0.0.0-SNAPSHOT.jar  StartFlow`

Once a flow has been pushed, one of the Flow workers should pick it up and run it