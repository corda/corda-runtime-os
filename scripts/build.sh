#!/bin/sh

#check for docker 
if ! docker info > /dev/null 2>&1; then
  echo "This script uses docker, and it isn't running - please start docker and try again!"
  exit 1
fi

#check for buildkit deamon
if [ "$(docker inspect -f '{{.State.Running}}' "buildkitd" 2>/dev/null)" = "true" ]
then echo "daemon running"
else docker run -d --name buildkitd --privileged moby/buildkit:latest
fi

export BUILDKIT_HOST=docker-container://buildkitd

#cp ../corda-cli-plugin-host/app/build/tmp/publishOSGiImage/containerization/cli.jar ./docker/plugins/cli.jar

#buildctl build --frontend=dockerfile.v0 \
#    --local context=. \
#    --local dockerfile=. \
#    --opt build-arg:workerType=combined \
#    --output type=docker,name=corda-os-docker-dev.software.r3.com/corda-os-combined-worker:latest-local | docker load


#bash -c "buildctl build --frontend=dockerfile.v0 \
#    --local context=. \
#    --local dockerfile=./docker/workers \
#    --opt build-arg:workerType=crypto \
#    --output type=docker,name=corda-os-docker-dev.software.r3.com/corda-os-crypto-worker:latest-local | docker load" &
#
#bash -c "buildctl build --frontend=dockerfile.v0 \
#    --local context=. \
#    --local dockerfile=./docker/workers \
#    --opt build-arg:workerType=rpc \
#    --output type=docker,name=corda-os-docker-dev.software.r3.com/corda-os-rpc-worker:latest-local | docker load" &
#
#bash -c "buildctl build --frontend=dockerfile.v0 \
#    --local context=. \
#    --local dockerfile=./docker/workers \
#    --opt build-arg:workerType=flow \
#    --output type=docker,name=corda-os-docker-dev.software.r3.com/corda-os-flow-worker:latest-local | docker load" &
#
#bash -c "buildctl build --frontend=dockerfile.v0 \
#    --local context=. \
#    --local dockerfile=./docker/workers \
#    --opt build-arg:workerType=member \
#    --output type=docker,name=corda-os-docker-dev.software.r3.com/corda-os-member-worker:latest-local | docker load" &
#
#bash -c "buildctl build --frontend=dockerfile.v0 \
#    --local context=. \
#    --local dockerfile=./docker/workers \
#    --opt build-arg:workerType=db \
#    --output type=docker,name=corda-os-docker-dev.software.r3.com/corda-os-db-worker:latest-local | docker load" &

#bash -c "buildctl build --frontend=dockerfile.v0 \
#    --local context=. \
#    --local dockerfile=./docker/plugins \
#    --opt build-arg:workerType=db \
#    --output type=docker,name=corda-os-docker-dev.software.r3.com/corda-os-plugins:latest-local | docker load" 
#
#wait

#bash -c "buildctl build --frontend=dockerfile.v0 \
#    --local context=. \
#    --local dockerfile=. \
#    --opt build-arg:workerType=crypto \
#    --output type=docker,name=crypto:buildctl > crypto.tar" &
#
#
#curl crypto.tar to somewhere

bash -c "buildctl build --frontend=dockerfile.v0 \
    --local context=. \
    --local dockerfile=./docker \
    --opt build-arg:BASE_IMAGE=corda-os-docker.software.r3.com/corda-os-cli:unstable \
    --opt build-arg:BUILD_PATH=/tools/plugins/build/tmp/publishOSGiImage/containerization \
    --opt build-arg:JAR_LOCATION=/opt/override/plugins/ \
    --opt build-arg:IMAGE_ENTRYPOINT=\"exec java -Dlog4j.configurationFile=log4j2-console.xml -Dpf4j.pluginsDir=/opt/override/plugins -Dlog4j.configurationFile=log4j2-console.xml -jar /opt/override/cli.jar\" \
    --output type=docker,name=corda-os-docker-dev.software.r3.com/corda-os-plugins:latest-local | docker load" &

bash -c "buildctl build --frontend=dockerfile.v0 \
    --local context=. \
    --local dockerfile=./docker \
    --opt build-arg:BASE_IMAGE=azul/zulu-openjdk:11.0.5\
    --opt build-arg:BUILD_PATH=/applications/workers/release/crypto-worker/build/bin/corda-crypto-worker-5.0.0.0-SNAPSHOT.jar \
    --opt build-arg:JAR_LOCATION=/opt/override/corda-crypto-worker.jar\
    --opt build-arg:IMAGE_ENTRYPOINT=\"exec java -Dlog4j2.debug=false -Dlog4j.configurationFile=log4j2-console.xml -jar /opt/override/corda-crypto-worker.jar\" \
    --output type=docker,name=corda-os-docker-dev.software.r3.com/corda-os-crypto-worker:latest-local | docker load" &

bash -c "buildctl build --frontend=dockerfile.v0 \
    --local context=. \
    --local dockerfile=./docker \
    --opt build-arg:BASE_IMAGE=azul/zulu-openjdk:11.0.5 \
    --opt build-arg:BUILD_PATH=/applications/workers/release/flow-worker/build/bin/corda-flow-worker-5.0.0.0-SNAPSHOT.jar \
    --opt build-arg:JAR_LOCATION=/opt/override/corda-flow-worker.jar \
    --opt build-arg:IMAGE_ENTRYPOINT=\"exec java -Dlog4j2.debug=false -Dlog4j.configurationFile=log4j2-console.xml -jar /opt/override/corda-flow-worker.jar\" \
    --output type=docker,name=corda-os-docker-dev.software.r3.com/corda-os-flow-worker:latest-local | docker load" &

bash -c "buildctl build --frontend=dockerfile.v0 \
    --local context=. \
    --local dockerfile=./docker \
    --opt build-arg:BASE_IMAGE=azul/zulu-openjdk:11.0.5 \
    --opt build-arg:BUILD_PATH=/applications/workers/release/db-worker/build/bin/corda-db-worker-5.0.0.0-SNAPSHOT.jar \
    --opt build-arg:JAR_LOCATION=/opt/override/corda-db-worker.jar \
    --opt build-arg:IMAGE_ENTRYPOINT=\"exec java -Dlog4j2.debug=false -Dlog4j.configurationFile=log4j2-console.xml -jar /opt/override/corda-db-worker.jar\" \
    --output type=docker,name=corda-os-docker-dev.software.r3.com/corda-os-db-worker:latest-local | docker load" &

bash -c "buildctl build --frontend=dockerfile.v0 \
    --local context=. \
    --local dockerfile=./docker \
    --opt build-arg:BASE_IMAGE=azul/zulu-openjdk:11.0.5 \
    --opt build-arg:BUILD_PATH=/applications/workers/release/member-worker/build/bin/corda-member-worker-5.0.0.0-SNAPSHOT.jar \
    --opt build-arg:JAR_LOCATION=/opt/override/corda-member-worker.jar \
    --opt build-arg:IMAGE_ENTRYPOINT=\"exec java -Dlog4j2.debug=false -Dlog4j.configurationFile=log4j2-console.xml -jar /opt/override/corda-member-worker.jar\" \
    --output type=docker,name=corda-os-docker-dev.software.r3.com/corda-os-member-worker:latest-local | docker load" &

bash -c "buildctl build --frontend=dockerfile.v0 \
    --local context=. \
    --local dockerfile=./docker \
    --opt build-arg:BASE_IMAGE=azul/zulu-openjdk:11.0.5 \
    --opt build-arg:BUILD_PATH=/applications/workers/release/rpc-worker/build/bin/corda-rpc-worker-5.0.0.0-SNAPSHOT.jar \
    --opt build-arg:JAR_LOCATION=/opt/override/corda-rpc-worker.jar \
    --opt build-arg:IMAGE_ENTRYPOINT=\"exec java -Dlog4j2.debug=false -Dlog4j.configurationFile=log4j2-console.xml -jar /opt/override/corda-rpc-worker.jar\" \
    --output type=docker,name=corda-os-docker-dev.software.r3.com/corda-os-rpc-worker:latest-local | docker load"

wait

#rm  ./docker/plugins/cli.jar

#cleanup
#docker stop "buildkitd"
#docker rm "buildkitd"