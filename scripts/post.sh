docker run -d --name buildkitd --privileged moby/buildkit:latest
export BUILDKIT_HOST=docker-container://buildkitd

docker login docker-js-temp.software.r3.com -p $CORDA_ARTIFACTORY_PASSWORD  -u $CORDA_ARTIFACTORY_USERNAME

cd ../

buildctl build --frontend=dockerfile.v0 \
    --local context=. \
    --local dockerfile=./docker \
    --opt build-arg:BASE_IMAGE=corda-os-docker.software.r3.com/corda-os-cli:unstable \
    --opt build-arg:BUILD_PATH=/tools/plugins/build/tmp/publishOSGiImage/containerization \
    --opt build-arg:JAR_LOCATION=/opt/override/plugins/ \
    --opt build-arg:IMAGE_ENTRYPOINT="exec java -Dlog4j.configurationFile=log4j2-console.xml -Dpf4j.pluginsDir=/opt/override/plugins -Dlog4j.configurationFile=log4j2-console.xml -jar /opt/override/cli.jar" \
    --output type=docker,name=docker-js-temp.software.r3.com/corda-os-plugins > corda-os-plugins.tar

wait


./scripts/publish.sh corda-os-plugins.tar https://software.r3.com:443/artifactory/docker-js-temp/ $CORDA_ARTIFACTORY_USERNAME $CORDA_ARTIFACTORY_PASSWORD


