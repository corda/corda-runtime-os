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
          "publishAlias": "key1",
          "keystoreAlias": "1",
          "keystoreFile": "<path_to_the_keystore_file>",
          "password": "keystore-password",
          "tenantId": "tenantID"
        },
        {
          "publishAlias": "key2",
          "keystoreAlias": "2",
          "keystoreFile": "<path_to_the_keystore_file>",
          "password": "keystore-password",
          "tenantId": "tenantID"
        }
    ]
}
```
Where:
* `publish_alias` is a unique name to publish the keys under. If omitted, a random UUID will be used.
* `keystore_alias` is the keys alias in the key store. If omitted, the first alias will be used.
* `keystoreFile` is a valid JKS keystore file. You can create them using Java's `keytool`, e.g.:
```
keytool -genkeypair -alias ec -keyalg EC -storetype JKS -keystore ec_key.jks -storepass 123456
```
* `password` is the JKS keystore password.
* `tenantId` A non-unique ID for the tenant

### Populating a custom topic

The key entries can also be written to a custom topic using the `--topic` parameter:
```
java -jar applications/tools/p2p-test/cryptoservice-key-creator/build/bin/corda-cryptoservice-key-creator-5.0.0.0-SNAPSHOT.jar --keys-config ~/Desktop/keys-config.json --kafka ~/Desktop/kafka.properties --topic test.topic
```
