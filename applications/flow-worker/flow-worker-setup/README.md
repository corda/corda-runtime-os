from root dir: 
`gradlew clean applications:flow-worker:flow-worker-setup:appJar`

from flow-worker-setup dir:
`java -jar build/bin/corda-flow-worker-setup-5.0.0.0-SNAPSHOT.jar --instanceId=1 --config config.conf --topic topics.conf`
