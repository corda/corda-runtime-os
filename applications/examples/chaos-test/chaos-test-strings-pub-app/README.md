#Chaos Test Strings Publisher
This is a test publisher 'application' or Patterns/messaging library driver for use in chaos testing, similar to the 'demo-publisher'. It 'publishes' string data and includes --msgPrefix option to include a given string in messages sent.  
It is anticipated that it will be run by automated testing tools but it is also intended to be runnable 'by-hand' to allow ad-hoc testing and test development.  

##To build 
`gradlew clean appJar`

##An example execution 

    java -Dlog4j2.configurationFile=log4j2.xml \
        -Dbootstrap.servers=localhost:9092 \
        -Dmessaging.topic.prefix=demo \
        -jar corda-chaos-test-strings-pub-app-5.0.0.0-SNAPSHOT.jar \
        --instanceId 1 \
        --numberOfRecords 20 \
        --numberOfKeys 1 \
        --msgPrefix "my_unique_prefix:" 
                
