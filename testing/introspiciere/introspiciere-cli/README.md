# Introspiciere-cli

```shell
# cd into introspiciere-server directory
cd testing/introspiciere/introspiciere-cli

# compile
../../../gradlew build -x test 

# run
java -jar build/libs/introspiciere-cli-5.0.0.0-SNAPSHOT.jar
```

## Commands

### Create a topic

```shell
java -jar introspiciere-cli-5.0.0.0-SNAPSHOT.jar create-topic \
  --endpoint http://localhost:9094 --topic topic-1234 [--partitions 5] [--replicaton-factor 2]
```