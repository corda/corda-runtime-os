This is a tool that can be used to create network map data on Kafka, which can then be read by a Link Manager (via a network map implementation). 

The ultimate purpose of this tool is to test the p2p layer in an isolated way without requiring external components.

## Building the tool
```
./gradlew applications:tools:p2p-test:network-map-creator:clean
./gradlew applications:tools:p2p-test:network-map-creator:appJar
```

## Running the tool

```
java -jar applications/tools/p2p-test/network-map-creator/build/bin/corda-network-map-creator-5.0.0.0-SNAPSHOT.jar --netmap-file ~/Desktop/netmap.json --kafka ~/Desktop/kafka.properties
```

The file specified in the `--kafka` CLI parameter should have the following structure:
```
bootstrap.servers=localhost:9092
```

The file provided on the `--netmap-file` CLI parameter should have the following structure:

```json
{
  "entriesToAdd": [
    {
      "x500name": "O=Alice, L=London, C=GB",
      "groupId": "group-1",
      "data": {
        "publicKeyStoreFile": "<path_to_the_keystore_file>",
        "publicKeyAlias": "<alias_of_public_key>",
        "keystorePassword": "keystore-password",
        "address": "http://alice.com",
        "networkType": "CORDA_4",
        "trustStoreCertificates": [
          "<path_to_trust_certificate_files>"
        ]
      },
      "locallyHosted": {
        "tlsTenantId": "AliceId",
        "identityTenantId": "AliceId",
        "tlsCertificates": [
          "<path_to_tls_certificate_files>"
        ]
      }
    },
    {
      "x500name": "O=Bob, L=London, C=GB",
      "groupId": "group-2",
      "data": {
        "publicKeyStoreFile": "<path_to_the_keystore_file>",
        "publicKeyAlias": "<alias_of_public_key>",
        "keystorePassword": "keystore-password",
        "publicKeyAlgo": "ECDSA",
        "address": "http://bob.com",
        "networkType": "CORDA_5",
        "trustStoreCertificates": [
          "<path_to_trust_certificate_files>"
        ]
      },
      "locallyHosted": false
    }
  ],
  "entriesToDelete": [
    {
      "x500name": "O=Charlie, L=London, C=GB",
      "groupId": "group-1"
    }
  ]
}
```

Key store files are expected to be `.jks` files. You can create them using Java's `keytool`, e.g.:
```
keytool -genkeypair -alias ec -keyalg EC -storetype JKS -keystore ec_key.jks -storepass 123456
```

trust store files are expected to be `.pem` files.

### Populating a custom topics

The key entries can also be written to a custom topic using the `--network-map-topic` and/or `--locally-hosted-topic` parameter:
```
java -jar applications/tools/p2p-test/network-map-creator/build/bin/corda-network-map-creator-5.0.0.0-SNAPSHOT.jar --netmap-file ~/Desktop/keys-config.json --kafka ~/Desktop/kafka.properties --topic test.topic
```