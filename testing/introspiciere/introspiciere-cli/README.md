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
  --endpoint http://localhost:9094 --topic topic-1234 [--partitions 5] [--replication-factor 2]
```

### Write a message

```shell
java -jar introspiciere-cli-5.0.0.0-SNAPSHOT.jar << END write \
  --endpoint http://localhost:9094 --topic topic-1234 --key key1 --schema net.corda.p2p.test.KeyPairEntry
{
    "keyAlgo": "ECDSA",
    "publicKey": "binary-for-public-key",
    "privateKey": "binary-for-private-key"
}
END
```
