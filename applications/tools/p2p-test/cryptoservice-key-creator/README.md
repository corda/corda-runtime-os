This is a tool that can be used to create key pair entries on Kafka, which can then be read by a Link Manager (via a CryptoService implementation) to perform signatures during session authentication.

The ultimate purpose of this tool is to test the p2p layer in an isolated way without requiring external components.

## Building the tool
```
./gradlew applications:tools:p2p-test:cryptoservice-key-creator:clean
./gradlew applications:tools:p2p-test:cryptoservice-key-creator:appJar
```

## Running the tool

```
java -jar applications/tools/p2p-test/cryptoservice-key-creator/build/bin/corda-cryptoservice-key-creator-5.0.0.0-SNAPSHOT.jar --keys-config ~/Desktop/keys-config.json --kafka ~/Desktop/kafka.properties
```

The file specified in the `--kafka` CLI parameter should have the following structure:
```
bootstrap.servers=localhost:9092
```

The file provided on the `--keys-config` CLI parameter should have the following structure:
```json
{
    "keys": [
        {
          "alias": "key1",
          "keystoreFile": "<path_to_the_keystore_file>",
          "password": "keystore-password",
          "algo": "RSA",
          "holdingIdentity": {
            "x500name": "O=Alice, L=London, C=GB",
            "groupId": "group-1"
          }
        },
        {
          "alias": "key2",
          "keystoreFile": "<path_to_the_keystore_file>",
          "password": "keystore-password",
          "algo": "ECDSA",
          "tenantId": "0F0A1424B0E0"
        }
    ]
}
```
Note: the `alias` field acts as a unique identifier for each key pair entry and thus needs to be unique for each entry. If you want to deploy multiple identities behind a single host, make sure you use a different alias for the entry of each identity. 

Note: If the `tenantId` will be omitted, it will be calculated based on the `holdingIdentity`. If the `tenantId` will be provided, the `holdingIdentity` will be ignored.

Key files are expected to be `.jks` files. You can create them using Java's `keytool`, e.g.:
```
keytool -genkeypair -alias ec -keyalg EC -storetype JKS -keystore ec_key.jks -storepass 123456
```

### Populating a custom topic

The key entries can also be written to a custom topic using the `--topic` parameter:
```
java -jar applications/tools/p2p-test/cryptoservice-key-creator/build/bin/corda-cryptoservice-key-creator-5.0.0.0-SNAPSHOT.jar --keys-config ~/Desktop/keys-config.json --kafka ~/Desktop/kafka.properties --topic test.topic
```
