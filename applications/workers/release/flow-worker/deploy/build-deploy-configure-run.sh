gradlew clean applications:workers:release:flow-worker:publishOSGiImage applications:tools:flow-worker-setup:appJar testing:cpbs:helloworld:build

docker compose -p "Flow-worker" up

java -jar ../flow-worker/applications/tools/flow-worker-setup/build/bin/corda-flow-worker-setup-5.0.0.0-SNAPSHOT.jar PublishConfig SetupVirtualNode StartFlow --cpiDir C:/flow-worker/testing/cpbs/helloworld/build/libs  --config ../flow-worker/applications/tools/flow-worker-setup/config.conf

java -jar ../flow-worker/applications/tools/flow-worker-setup/build/bin/corda-flow-worker-setup-5.0.0.0-SNAPSHOT.jar  StartFlow

java -jar ../flow-worker/applications/workers/release/flow-worker/build/bin/corda-flow-worker-5.0.0.0-SNAPSHOT.jar --instanceId 1