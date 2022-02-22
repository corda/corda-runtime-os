# Introspiciere-cli

This is the temporary CLI to manually use introspiciere. It will eventually be integrated as a plugion of
the `corda-cli`.

## Build the cli

```shell
# cd into introspiciere-server directory and compile
cd testing/introspiciere/introspiciere-cli
../../../gradlew build -x test 
```

## Commands

Before running the commands make sure introspiciere server is running and is correctly connect to Kafka.

**Run Kafka and introspiciere locally**

1. Deploy Kafka following instructions in [introspiciere](../README.md)
2. Deploy introspiciere in a local process following instructions under [introspiciere-server](../introspiciere-server/README.md)

### Create a topic

```shell
java -jar introspiciere-cli-5.0.0.0-SNAPSHOT.jar create-topic \
  --endpoint http://localhost:7070 --topic topic-1234 [--partitions 5] [--replication-factor 2]
```

### Write a message

```shell
java -jar introspiciere-cli-5.0.0.0-SNAPSHOT.jar << END write \
  --endpoint http://localhost:7070 --topic topic-1234 --key key1 --schema net.corda.p2p.test.KeyPairEntry
{
    "keyAlgo": "ECDSA",
    "publicKey": "binary-for-public-key",
    "privateKey": "binary-for-private-key"
}
END
```

### Read messages

```shell
java -jar introspiciere-cli-5.0.0.0-SNAPSHOT.jar read \
  --endpoint http://localhost:7070 --topic topic-1234 --key key1 --schema net.corda.p2p.test.KeyPairEntry
```