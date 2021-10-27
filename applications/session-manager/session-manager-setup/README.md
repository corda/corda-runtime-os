from root dir: 
`gradlew clean applications:session-manager:session-manager:appJar`

from flow-worker-setup dir:
`java -jar build/bin/corda-session-manager-setup-5.0.0.0-SNAPSHOT.jar --instanceId=1 --config config.conf --topic topics.conf`
