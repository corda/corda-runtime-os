#!/bin/sh

while getopts b:p:j:e:n: flag
do
    case "${flag}" in
        b) base_image=${OPTARG};;
        p) build_path=${OPTARG};;
        j) jar_location=${OPTARG};;
        e) image_entrypoint=${OPTARG};;
        n) image_name=${OPTARG};;
    esac
done

args=$(getopt -o ep: -l example,project: -n "$0" -- "$@") || exit
eval set -- "$args"

while [[ $1 != '--' ]]; do
    case "$1" in
        -e|--base_image) echo "example";      shift 1;;
        -p|--project) echo "project = $2"; shift 2;;

        # shouldn't happen unless we're missing a case
        *) echo "unhandled option: $1" >&2; exit 1;;
    esac
done
shift

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
export CORDA_ARTIFACTORY_USERNAME=jan.szkaradek@r3.com
export CORDA_ARTIFACTORY_PASSWORD=AKCp8mYxymGWqjABN3pqfDc8Gdc4QsT1pp3nWgNwUCFs4xNchQyMdA71D29E9ucFdXod27WhH

docker login docker-js-temp.software.r3.com -p $CORDA_ARTIFACTORY_USERNAME  -u $CORDA_ARTIFACTORY_PASSWORD

buildctl build --frontend=dockerfile.v0 \
    --local context=. \
    --local dockerfile=./docker \
    --opt build-arg:BASE_IMAGE=$base_image \
    --opt build-arg:BUILD_PATH=$build_path \
    --opt build-arg:JAR_LOCATION=$jar_location \
    --opt build-arg:IMAGE_ENTRYPOINT=$image_entrypoint \
    --output type=image,name=$image_name,push=true

wait

docker pull $image_name