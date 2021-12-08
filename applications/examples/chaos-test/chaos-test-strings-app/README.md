#Chaos Test Strings Application
This is a test 'application' or Patterns/messaging library driver for use in chaos testing. This is similar to the 'demo-app', except that string, not integer, based messages are used. It can run set of publisher/subscribers apps as threads or individual publisher/subscribers as specified by the "--clientType" option.  
It is anticipated that it will be run by automated testing tools but it is also intended to be runnable 'by-hand' to allow ad-hoc testing and test development.

##To build
`gradlew clean appJar`

##Examples
### Run the Durable Subscriber/Publisher
    java -Dlog4j2.configurationFile=log4j2.xml \
        -Dbootstrap.servers=localhost:9092 \
        -Dmessaging.topic.prefix=demo \
        -jar corda-chaos-test-strings-app-5.0.0.0-SNAPSHOT.jar \
        --instanceId 1 \
        --clientType Durable

### Run the Subscriber
* NB: This depends on the Durable Subscriber/Publisher running


    java -Dlog4j2.configurationFile=log4j2.xml \
        -Dbootstrap.servers=localhost:9092 \
        -Dmessaging.topic.prefix=demo \
        -jar corda-chaos-test-strings-app-5.0.0.0-SNAPSHOT.jar \
        --instanceId 1 \
        --clientType Sub

### Run the State And Events Subscriber/Publisher Running
* NB: This depends on the Durable Subscriber/Publisher running


    java -Dlog4j2.configurationFile=log4j2.xml \
        -Dbootstrap.servers=localhost:9092 \
        -Dmessaging.topic.prefix=demo \
        -jar corda-chaos-test-strings-app-5.0.0.0-SNAPSHOT.jar \
        --instanceId 1 \
        --clientType StateEvent
