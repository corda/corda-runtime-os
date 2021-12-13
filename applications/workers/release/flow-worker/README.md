
from root project dir:
`gradlew clean applications:flow-worker:appJar testing:cpbs:helloworld:build`

from flow-worker app dir:
`java -jar corda-flow-worker-5.0.0.0-SNAPSHOT.jar --instanceId 1`

configure cpb dir(tmp solution until CPI service):
`java -Dcpb.directory=C:\dev\corda-runtime-os\testing\cpbs\helloworld\build\libs -jar corda-flow-worker-5.0.0.0-SNAPSHOT.jar --instanceId 1 --additionalParams config.topic.name="config.topic"`
