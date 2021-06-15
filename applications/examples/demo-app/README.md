gradlew clean appJar

run 3 kafka brokers with auto.create.topics.enable=false

create the non config topics via commandline:

`.\kafka-topics.bat --zookeeper localhost:2181 --create --topic publisherTopic --partitions 3 --replication-factor 3`

`.\kafka-topics.bat --zookeeper localhost:2181 --create --topic eventTopic --partitions 3 --replication-factor 3`

`.\kafka-topics.bat --zookeeper localhost:2181 --create --topic stateTopic --partitions 3 --replication-factor 3 --config cleanup.policy=compact`

`.\kafka-topics.bat --zookeeper localhost:2181 --create --topic pubsubTopic --partitions 3 --replication-factor 3`


run 3 workers from separate dirs in the resource folder. For example:

- From resources/node1: java -jar ..\..\..\..\build\bin\corda-demo-app-5.0.0-SNAPSHOT.jar --instanceId 1 --kafka ..\kafka.properties --topic ..\topic.conf --config ..\config.conf
- From resources/node2: java -jar ..\..\..\..\build\bin\corda-demo-app-5.0.0-SNAPSHOT.jar --instanceId 2 --kafka ..\kafka.properties --topic ..\topic.conf --config ..\config.conf
- From resources/node3: java -jar ..\..\..\..\build\bin\corda-demo-app-5.0.0-SNAPSHOT.jar --instanceId 3 --kafka ..\kafka.properties --topic ..\topic.conf --config ..\config.conf

