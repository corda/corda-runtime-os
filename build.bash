#!/bin/bash

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
#    --output type=docker,name=combined:buildctl | docker load


bash -c "buildctl build --frontend=dockerfile.v0 \
    --local context=. \
    --local dockerfile=./docker/workers \
    --opt build-arg:workerType=crypto \
    --output type=docker,name=crypto:buildctl | docker load" &


bash -c "buildctl build --frontend=dockerfile.v0 \
    --local context=. \
    --local dockerfile=./docker/workers \
    --opt build-arg:workerType=rpc \
    --output type=docker,name=rpc:buildctl | docker load" &

bash -c "buildctl build --frontend=dockerfile.v0 \
    --local context=. \
    --local dockerfile=./docker/workers \
    --opt build-arg:workerType=flow \
    --output type=docker,name=flow:buildctl | docker load" &

bash -c "buildctl build --frontend=dockerfile.v0 \
    --local context=. \
    --local dockerfile=./docker/workers \
    --opt build-arg:workerType=member \
    --output type=docker,name=member:buildctl | docker load" &

bash -c "buildctl build --frontend=dockerfile.v0 \
    --local context=. \
    --local dockerfile=./docker/workers \
    --opt build-arg:workerType=db \
    --output type=docker,name=db:buildctl | docker load" &

bash -c "buildctl build --frontend=dockerfile.v0 \
    --local context=. \
    --local dockerfile=./docker/plugins \
    --opt build-arg:workerType=db \
    --output type=docker,name=plugins:buildctl | docker load" 

wait

#rm  ./docker/plugins/cli.jar

#cleanup
#docker stop "buildkitd"
#docker rm "buildkitd"