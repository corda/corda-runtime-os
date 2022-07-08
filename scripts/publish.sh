#check for docker 
#if ! docker info > /dev/null 2>&1; then
#  echo "This script uses docker, and it isn't running - please start docker and try again!"
#  exit 1
#fi

#check for buildkit deamon
#if [ "$(docker inspect -f '{{.State.Running}}' "buildkitd" 2>/dev/null)" = "true" ]
#then echo "daemon running"
#else docker run -d --name buildkitd --privileged moby/buildkit:latest
#fi

export CORDA_ARTIFACTORY_USERNAME=jan.szkaradek@r3.com
export CORDA_ARTIFACTORY_PASSWORD=AKCp8mYxymGWqjABN3pqfDc8Gdc4QsT1pp3nWgNwUCFs4xNchQyMdA71D29E9ucFdXod27WhH 

kubectl apply -f .certs/buildkit-daemon-certs.yaml
kubectl create secret docker-registry docker-registry-cred \
  --docker-server "docker-js-temp.software.r3.com" \
  --docker-username $CORDA_ARTIFACTORY_USERNAME \
  --docker-password $CORDA_ARTIFACTORY_PASSWORD
kubectl apply -f ./kubernetes/deployment+service.rootless.yaml
kubectl scale --replicas=6 deployment/buildkitd

kubectl wait --for=condition=Ready deployment/buildkitd

wait

kubectl port-forward service/buildkitd 1234 &

mkdir -p ./tools/plugins/build/tmp/buildkit/containerization

cp ./tools/plugins/db-config/build/libs/db-config-cli-plugin-5.0.0.0-SNAPSHOT.jar ./tools/plugins/build/tmp/buildkit/containerization/db-config-cli-plugin.jar
cp ./tools/plugins/db-config/build/libs/plugin-dbconfig-5.0.0.0-SNAPSHOT-5.0.0.0-SNAPSHOT.jar ./tools/plugins/build/tmp/buildkit/containerization/plugin-dbconfig.jar
cp ./tools/plugins/initial-config/build/libs/initial-config-cli-plugin-5.0.0.0-SNAPSHOT.jar ./tools/plugins/build/tmp/buildkit/containerization/initial-config-cli-plugin.jar
cp ./tools/plugins/initial-config/build/libs/plugin-plugin-initial-config-5.0.0.0-SNAPSHOT-5.0.0.0-SNAPSHOT.jar ./tools/plugins/build/tmp/buildkit/containerization/plugin-plugin-initial-config.jar
cp ./tools/plugins/secret-config/build/libs/secret-config-cli-plugin-5.0.0.0-SNAPSHOT.jar ./tools/plugins/build/tmp/buildkit/containerization/secret-config-cli-plugin.jar
cp ./tools/plugins/secret-config/build/libs/plugin-plugin-secret-config-5.0.0.0-SNAPSHOT-5.0.0.0-SNAPSHOT.jar ./tools/plugins/build/tmp/buildkit/containerization/plugin-plugin-secret-config.jar
cp ./tools/plugins/topic-config/build/libs/topic-config-cli-plugin-5.0.0.0-SNAPSHOT.jar ./tools/plugins/build/tmp/buildkit/containerization/topic-config-cli-plugin.jar
cp ./tools/plugins/topic-config/build/libs/plugin-topicconfig-5.0.0.0-SNAPSHOT-5.0.0.0-SNAPSHOT.jar ./tools/plugins/build/tmp/buildkit/containerization/plugin-topicconfig.jar
cp ./tools/plugins/virtual-node/build/libs/virtual-node-cli-plugin-5.0.0.0-SNAPSHOT.jar ./tools/plugins/build/tmp/buildkit/containerization/virtual-node-cli-plugin.jar
cp ./tools/plugins/virtual-node/build/libs/plugin-virtual-node-5.0.0.0-SNAPSHOT.jar ./tools/plugins/build/tmp/buildkit/containerization/plugin-virtual-node.jar


buildctl \
    --addr tcp://127.0.0.1:1234 \
    --tlscacert .certs/client/ca.pem \
    --tlscert .certs/client/cert.pem \
    --tlskey .certs/client/key.pem \
build --frontend=dockerfile.v0 \
    --local context=. \
    --local dockerfile=./docker \
    --opt build-arg:BASE_IMAGE=corda-os-docker.software.r3.com/corda-os-cli:unstable \
    --opt build-arg:BUILD_PATH=/tools/plugins/build/tmp/publishOSGiImage/containerization \
    --opt build-arg:JAR_LOCATION=/opt/override/plugins/ \
    --opt build-arg:IMAGE_ENTRYPOINT=\"exec java -Dlog4j.configurationFile=log4j2-console.xml -Dpf4j.pluginsDir=/opt/override/plugins -Dlog4j.configurationFile=log4j2-console.xml -jar /opt/override/cli.jar\" \
    --output type=image,name=docker-js-temp.software.r3.com/corda-os-plugins,push=true \
    --export-cache type=registry,ref=docker-js-temp.software.r3.com/corda-os-plugins-cache \
    --import-cache type=registry,ref=docker-js-temp.software.r3.com/corda-os-plugins-cache &

buildctl \
    --addr tcp://127.0.0.1:1234 \
    --tlscacert .certs/client/ca.pem \
    --tlscert .certs/client/cert.pem \
    --tlskey .certs/client/key.pem \
build --frontend=dockerfile.v0 \
    --local context=. \
    --local dockerfile=./docker \
    --opt build-arg:BASE_IMAGE=azul/zulu-openjdk:11.0.5\
    --opt build-arg:BUILD_PATH=/applications/workers/release/crypto-worker/build/bin/corda-crypto-worker-5.0.0.0-SNAPSHOT.jar \
    --opt build-arg:JAR_LOCATION=/opt/override/corda-crypto-worker.jar\
    --opt build-arg:IMAGE_ENTRYPOINT=\"exec java -Dlog4j2.debug=false -Dlog4j.configurationFile=log4j2-console.xml -jar /opt/override/corda-crypto-worker.jar\" \
    --output type=image,name=docker-js-temp.software.r3.com/corda-os-crypto-worker,push=true \
    --export-cache type=registry,ref=docker-js-temp.software.r3.com/corda-os-crypto-worker-cache \
    --import-cache type=registry,ref=docker-js-temp.software.r3.com/corda-os-crypto-worker-cache &

buildctl \
    --addr tcp://127.0.0.1:1234 \
    --tlscacert .certs/client/ca.pem \
    --tlscert .certs/client/cert.pem \
    --tlskey .certs/client/key.pem \
build --frontend=dockerfile.v0 \
    --local context=. \
    --local dockerfile=./docker \
    --opt build-arg:BASE_IMAGE=azul/zulu-openjdk:11.0.5 \
    --opt build-arg:BUILD_PATH=/applications/workers/release/flow-worker/build/bin/corda-flow-worker-5.0.0.0-SNAPSHOT.jar \
    --opt build-arg:JAR_LOCATION=/opt/override/corda-flow-worker.jar \
    --opt build-arg:IMAGE_ENTRYPOINT=\"exec java -Dlog4j2.debug=false -Dlog4j.configurationFile=log4j2-console.xml -jar /opt/override/corda-flow-worker.jar\" \
    --output type=image,name=docker-js-temp.software.r3.com/corda-os-flow-worker,push=true \
    --export-cache type=registry,ref=docker-js-temp.software.r3.com/corda-os-flow-worker-cache \
    --import-cache type=registry,ref=docker-js-temp.software.r3.com/corda-os-flow-worker-cache &
    
    
buildctl \
    --addr tcp://127.0.0.1:1234 \
    --tlscacert .certs/client/ca.pem \
    --tlscert .certs/client/cert.pem \
    --tlskey .certs/client/key.pem \
build --frontend=dockerfile.v0 \
    --local context=. \
    --local dockerfile=./docker \
    --opt build-arg:BASE_IMAGE=azul/zulu-openjdk:11.0.5 \
    --opt build-arg:BUILD_PATH=/applications/workers/release/db-worker/build/bin/corda-db-worker-5.0.0.0-SNAPSHOT.jar \
    --opt build-arg:JAR_LOCATION=/opt/override/corda-db-worker.jar \
    --opt build-arg:IMAGE_ENTRYPOINT=\"exec java -Dlog4j2.debug=false -Dlog4j.configurationFile=log4j2-console.xml -jar /opt/override/corda-db-worker.jar\" \
    --output type=image,name=docker-js-temp.software.r3.com/corda-os-db-worker,push=true \
    --export-cache type=registry,ref=docker-js-temp.software.r3.com/corda-os-db-worker-cache \
    --import-cache type=registry,ref=docker-js-temp.software.r3.com/corda-os-db-worker-cache &

buildctl \
    --addr tcp://127.0.0.1:1234 \
    --tlscacert .certs/client/ca.pem \
    --tlscert .certs/client/cert.pem \
    --tlskey .certs/client/key.pem \
build --frontend=dockerfile.v0 \
    --local context=. \
    --local dockerfile=./docker \
    --opt build-arg:BASE_IMAGE=azul/zulu-openjdk:11.0.5 \
    --opt build-arg:BUILD_PATH=/applications/workers/release/member-worker/build/bin/corda-member-worker-5.0.0.0-SNAPSHOT.jar \
    --opt build-arg:JAR_LOCATION=/opt/override/corda-member-worker.jar \
    --opt build-arg:IMAGE_ENTRYPOINT=\"exec java -Dlog4j2.debug=false -Dlog4j.configurationFile=log4j2-console.xml -jar /opt/override/corda-member-worker.jar\" \
    --output type=image,name=docker-js-temp.software.r3.com/corda-os-member-worker,push=true \
    --export-cache type=registry,ref=docker-js-temp.software.r3.com/corda-os-member-worker-cache \
    --import-cache type=registry,ref=docker-js-temp.software.r3.com/corda-os-member-worker-cache &

buildctl \
    --addr tcp://127.0.0.1:1234 \
    --tlscacert .certs/client/ca.pem \
    --tlscert .certs/client/cert.pem \
    --tlskey .certs/client/key.pem \
build --frontend=dockerfile.v0 \
    --local context=. \
    --local dockerfile=./docker \
    --opt build-arg:BASE_IMAGE=azul/zulu-openjdk:11.0.5 \
    --opt build-arg:BUILD_PATH=/applications/workers/release/rpc-worker/build/bin/corda-rpc-worker-5.0.0.0-SNAPSHOT.jar \
    --opt build-arg:JAR_LOCATION=/opt/override/corda-rpc-worker.jar \
    --opt build-arg:IMAGE_ENTRYPOINT=\"exec java -Dlog4j2.debug=false -Dlog4j.configurationFile=log4j2-console.xml -jar /opt/override/corda-rpc-worker.jar\" \
    --output type=image,name=docker-js-temp.software.r3.com/corda-os-rpc-worker,push=true \
    --export-cache type=registry,ref=docker-js-temp.software.r3.com/corda-os-rpc-worker-cache \
    --import-cache type=registry,ref=docker-js-temp.software.r3.com/corda-os-rpc-worker-cache

kubectl delete deploy buildkitd

#
#docker stop "buildkitd"
#docker rm "buildkitd"
#
#docker pull docker-js-temp.software.r3.com/corda-os-plugins:latest
#docker pull docker-js-temp.software.r3.com/corda-os-crypto-worker:latest
#docker pull docker-js-temp.software.r3.com/corda-os-flow-worker:latest
#docker pull docker-js-temp.software.r3.com/corda-os-member-worker:latest
#docker pull docker-js-temp.software.r3.com/corda-os-db-worker:latest
#docker pull docker-js-temp.software.r3.com/corda-os-rpc-worker:latest
#
#docker tag docker-js-temp.software.r3.com/corda-os-crypto-worker:latest corda-os-docker-dev.software.r3.com/corda-os-crypto-worker:latest-local
#docker tag docker-js-temp.software.r3.com/corda-os-member-worker:latest corda-os-docker-dev.software.r3.com/corda-os-member-worker:latest-local
#docker tag docker-js-temp.software.r3.com/corda-os-rpc-worker:latest corda-os-docker-dev.software.r3.com/corda-os-rpc-worker:latest-local
#docker tag docker-js-temp.software.r3.com/corda-os-flow-worker:latest corda-os-docker-dev.software.r3.com/corda-os-flow-worker:latest-local
#docker tag docker-js-temp.software.r3.com/corda-os-db-worker:latest corda-os-docker-dev.software.r3.com/corda-os-db-worker:latest-local
#docker tag docker-js-temp.software.r3.com/corda-os-plugins:latest corda-os-docker-dev.software.r3.com/corda-os-plugins:latest-local
#
