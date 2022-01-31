# Flow Worker Setup
This tool is designed as a throw away tool for driving the flow processing components during dev and debug. It is not 
intended as fully featured test or setup tool.

The purpose of this tool is to run one or more pre-defined, ordered tasks that can publish messages to kafka.

each task is defined as a simple class with access to a context that allows basic kafka operations such as create/delete
topics or publish messages.

the current version assumes kafka is running on localhost:9092

## Tasks
- *DeleteAllTopics* - deletes all topics on the kafka instance
- *CreateTopics* - creates all the topics needed to run the flow and associated components
- *PublishConfig* - publishes configurations sections from a file to the config topic. The file is passed in via the
  `--config` argument
- *SetupVirtualNode* - Publishes the vNode, CPI and CPK meta data onto Kafka. The `--cpiDir` specifies the folder where 
the CBP is located
- *StartFlow* - Publishes a Start Flow event to kafka, at the moment this is hard coded to the hello world example app

## Usage
to run the app specify a list of one or more of the tasks as command args + any optional args required by specific tasks
e.g.

Delete topics only  
`java -jar build/bin/corda-flow-worker-setup-5.0.0.0-SNAPSHOT.jar DeleteAllTopics`

Full restart  
`java -jar build/bin/corda-flow-worker-setup-5.0.0.0-SNAPSHOT.jar DeleteAllTopics CreateTopics SetupVirtualNode 
PublishConfig StartFlow --cpiDir C:/ows/git-repo/corda-runtime-os-build/testing/cpbs/helloworld/build/libs 
--config config.conf
`
