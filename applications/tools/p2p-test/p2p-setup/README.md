This is a tool that can be used to setup a P2P deployment. 

## Building the tool
```
./gradlew applications:tools:p2p-test:p2p-setup:clean applications:tools:p2p-test:p2p-setup:appJar
```

## Running the tool

### Creating a membership group
To create a membership group:
1. Make sure you have a valid PEM file containing the TLS trust roots of the group.
2. Prepare a configuration file with the group data. For example:
```json
{
  "groupId": "group-1",
  "data": {
    "networkType": "CORDA_5",
    "protocolModes": ["AUTHENTICATION_ONLY", "AUTHENTICATED_ENCRYPTION"],
    "trustRootCertificates":["-----BEGIN CERTIFICATE-----\nMIIDrjCCAhagAwIBAgIBAj...4w==\n-----END CERTIFICATE-----\n"]
  }
}
```
Where:
* `networkType` is either `CORDA_5`, `CORDA_4`.
* `protocolModes` are either: `AUTHENTICATED_ENCRYPTION` or `AUTHENTICATION_ONLY`.
* `trustRootCertificates` is the content of the root certificates (in PEM format).
* Instead, one can use `trustRootCertificatesFiles` with the paths to the trust store certificate in PEM format.

5. Run the command:
```bash
java -jar applications/tools/p2p-test/p2p-setup/build/bin/corda-p2p-setup-5.0.0.0-SNAPSHOT.jar \
  -k <kafka-host:kafka-port> \
  add-group <path_to_member_json_file>
```

### Publishing a group member
To publish a group member:
1. Make sure you have a valid PEM file containing the public key of the member.
2. Prepare a configuration file with the member data. For example:
```json
{
  "x500name": "O=Alice, L=London, C=GB",
  "groupId": "group-1",
  "data": {
       "publicKey": "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w...xwIDAQAB\n-----END PUBLIC KEY-----",
       "address": "http://alice.com:8080"
  }
}
```
Where:
* `publicKey` is the public key in PEM format
* `address` is the HTTP address of the member (In the format: `http://<host>:<port>/`)
* `publicKeyFile` can be an alternative to `publicKey` with path to the public key in PEM format.

5. Run the command:
```bash
java -jar applications/tools/p2p-test/p2p-setup/build/bin/corda-p2p-setup-5.0.0.0-SNAPSHOT.jar \
  -k <kafka-host:kafka-port> \
  add-member <path_to_member_json_file>
```

To create the public key from the private key use:
```bash
openssl ec -in private.ec.key -pubout -out public.ec.pem
```
or
```bash
openssl rsa -in private.rsa.key -pubout > public.rsa.pem
```


### Publishing an entry for a locally hosted identity
To publish an entry for a locally hosted identity:
1. Make sure you have a valid PEM file containing the TLS certificate chain.
2. Prepare a configuration file with the identity data. For example:
```json
{
  "x500name": "O=Alice, L=London, C=GB",
  "groupId": "group-1",
  "data": {
    "tlsTenantId": "cluster",
    "sessionKeyTenantId": "alice",
    "tlsCertificates":["-----BEGIN CERTIFICATE-----\nMIIDwDCCAiigAwI...tkIEaQ==\n-----END CERTIFICATE-----\n"]
  }
}
```
* The `tlsTenantId` is the tenant ID under which the TLS key is stored.
* The `tlsCertificates` should contain the content of the certificates (in PEM format).
* The `tlsCertificatesFiles` can be used as an alternative to `tlsCertificates` with a path to a valid certificate file (in PEM format).
* The `sessionKeyTenantId` is the tenant ID under which the session initiation key is stored.

4. Run the command:
```bash
java -jar applications/tools/p2p-test/p2p-setup/build/bin/corda-p2p-setup-5.0.0.0-SNAPSHOT.jar \
  -k <kafka-host:kafka-port> \
  add-identity <path_to_identity_json_file>
```

### Add cryptographic keys.
This command can be used to add to the cluster the cryptographic keys for TLS and session initiation.
To publish key-pair:
1. Make sure you have the private key file (in PEM format).
2. Prepare a configuration file with the identity data. For example:
```json
{
  "keys": "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEAsqXqc40Lq...+4zJ2Erl\n-----END RSA PRIVATE KEY-----\n",
  "tenantId": "tenant-alice-1",
  "publishAlias": "alice-identity-key"
}
```
Where:
* `publish_alias` is a unique name to publish the keys under. If omitted, a random UUID will be used.
* `keys` is the content of the private key in PEM format (use either that or `keysFile`).
* `keysFile` is the path to the file with the keys in PEM format.
* `tenantId` A non-unique ID for the tenant

5. Run the command:
```bash
java -jar applications/tools/p2p-test/p2p-setup/build/bin/corda-p2p-setup-5.0.0.0-SNAPSHOT.jar \
  -k <kafka-host:kafka-port> \
  add-key-pair <path_to_keys_json_file>
```
### Publish Gateway configuration.
Run the command:
```bash
java -jar applications/tools/p2p-test/p2p-setup/build/bin/corda-p2p-setup-5.0.0.0-SNAPSHOT.jar \
  -k <kafka-host:kafka-port> \
  config-gateway --port=<gateway-port>
```

Use `config-gateway --help` to view other configuration option.

### Publish Link manager configuration.
Run the command:
```bash
java -jar applications/tools/p2p-test/p2p-setup/build/bin/corda-p2p-setup-5.0.0.0-SNAPSHOT.jar \
  -k <kafka-host:kafka-port> \
  config-link-manager --maxReplayingMessages=200
```

Use `config-link-manager --help` to view other configuration option.

### Removing membership group
To remove a created membership group run the command:
```bash
java -jar applications/tools/p2p-test/p2p-setup/build/bin/corda-p2p-setup-5.0.0.0-SNAPSHOT.jar \
  -k <kafka-host:kafka-port> \
  remove-group <group-id>
```

### Removing a locally hosted identity
To remove a locally hosted identity run the command:
```bash
java -jar applications/tools/p2p-test/p2p-setup/build/bin/corda-p2p-setup-5.0.0.0-SNAPSHOT.jar \
  -k <kafka-host:kafka-port> \
  remove-identity -g <group-id> -x <x500-name>
```

### Kafka servers environment variable
If the `-k <kafka-servers>` command line argument is missing the environment variable `KAFKA_SERVERS` will be used by default.

### Populating a custom topic

Each sub command has a `--topic` option to overwrite the default topic where data will be published for each type of data, unless specified otherwise.

### Running multiple sub commands
One can add multiple sub commands in one run, for example:
```bash
java -jar applications/tools/p2p-test/p2p-setup/build/bin/corda-p2p-setup-5.0.0.0-SNAPSHOT.jar \
  -k localhost:9092 \
  add-group ~/Desktop/group1.json \
  add-member ~/Desktop/alice.json \
  add-member ~/Desktop/bob.json \
  add-member ~/Desktop/carol.json \
  add-keys ~/Desktop/tls-keys.json \
  add-keys ~/Desktop/identity-keys.json \
  add-identity ~/Desktop/alice-identity.json \
  config-gateway --port 8033 \
  config-link-manager
```

### Apply from a file
One can also save all the setup in a single JSON file, and apply it in one command.
The file should look like:
```json
{
  "linkManagerConfig": {
    "maxMessageSize": 1000000,
    "messageReplay": {
      "BasePeriod": 2000,
      "Cutoff": 10000,
      "MaxMessages": 100
    },
    "heartbeatMessagePeriod": 2000,
    "sessionTimeout": 10000
  },
  "gatewayConfig": {
    "hostAddress": "0.0.0.0",
    "hostPort": 8088,
    "sslConfig": {
      "revocationCheck": {
        "mode": "OFF"
      }
    },
    "connectionConfig": {
      "maxClientConnections": 100,
      "acquireTimeout": 10000,
      "connectionIdleTimeout": 60000,
      "responseTimeout": 1000,
      "retryDelay": 1000,
      "initialReconnectionDelay": 1000,
      "maximalReconnectionDelay": 16000
    }
  },
  "groupsToAdd": [
    {
      "groupId": "group-2",
      "data": {
        "networkType": "CORDA_5",
        "protocolModes": ["AUTHENTICATION_ONLY", "AUTHENTICATED_ENCRYPTION"],
        "trustRootCertificates":["-----BEGIN CERTIFICATE-----\nMIIDrjCCAhagAwIBAgIBAj...4w==\n-----END CERTIFICATE-----\n"]
      }
    }
  ],
  "membersToAdd": [
    {
      "x500name": "O=Alice, L=London, C=GB",
      "groupId": "group-1",
      "data": {
        "publicKeyFile": "<path_to_the_public_key_pem_file>",
        "address": "http://alice.com:8080"
      }
    }
  ],
  "identitiesToAdd": [
    {
      "x500name": "O=Alice, L=London, C=GB",
      "groupId": "group-2",
      "data": {
        "tlsTenantId": "cluster",
        "sessionKeyTenantId": "alice",
        "tlsCertificates": ["-----BEGIN CERTIFICATE-----\nMIIDwDCCAiigAwI...tkIEaQ==\n-----END CERTIFICATE-----\n"]
      }
    }
  ],
  "keysToAdd": [
    {
      "keys": "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEAsqXqc40Lq...+4zJ2Erl\n-----END RSA PRIVATE KEY-----\n",
      "tenantId": "tenant-alice-1",
      "publishAlias": "alice-identity-key"
    }
  ],
  "groupsToRemove": ["group-3", "group-5"],
  "membersToRemove": [
    {
      "x500name": "O=Alice, L=London, C=GB",
      "groupId": "group-3"
    }
  ],
  "identitiesToRemove": [
    {
      "x500name": "O=Alice, L=London, C=GB",
      "groupId": "group-3"
    }
  ]
}
```

If an entry is missing from the file the command will be ignored. The structure of the group/member/identity/keys is the same as in the `add-` commands.

To run the command use:
```bash
java -jar applications/tools/p2p-test/p2p-setup/build/bin/corda-p2p-setup-5.0.0.0-SNAPSHOT.jar \
  -k localhost:9092 \
  apply ~/Desktop/setup.json \
```