# P2P Setup tool
This is a tool that can be used perform the following setup steps in a P2P deployment:
* add cryptographic keys to the cluster.
* add or remove a locally hosted identity in the cluster. 
* create or delete a membership group.
* add or remove a member from a group.
* publish configuration for p2p components (e.g. link manager and gateway).

## Building the tool

To build the JAR artefact, run:
```
./gradlew applications:tools:p2p-test:p2p-setup:clean applications:tools:p2p-test:p2p-setup:appJar
```
This will create an executable JAR in `applications/tools/p2p-test/p2p-setup/build/bin/`.

## Running the tool

### Adding cryptographic keys.
The following instructions can be used to add to the cluster the cryptographic keys for TLS and session initiation.
First:
1. Make sure you have the private key file (in PEM format).
2. Prepare a configuration file with the identity data. For example:
```json
{
  "keys": "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEAsqXqc40Lq...+4zJ2Erl\n-----END RSA PRIVATE KEY-----\n",
  "tenantId": "tenant-alice-1",
  "publishAlias": "alice-identity-key"
}
```
where:
* `publish_alias` is a unique name to publish the keys under. If omitted, a random UUID will be used.
* `keys` is the content of the private key in PEM format (use either this or `keysFile`).
* `keysFile` can be an alternative to `keys` with the path to a file containing the private key in PEM format.
* `tenantId` A non-unique ID for the tenant

Then, in order to publish the key pair, run:
```bash
java -jar applications/tools/p2p-test/p2p-setup/build/bin/corda-p2p-setup*.jar \
  -k broker1:9093 \
  add-key-pair <path_to_keys_json_file>
```

### Adding a locally hosted identity
The following instructions can be used to add a new locally hosted identity to a cluster.
First:
1. Make sure you have a valid PEM file containing the TLS certificate chain.
2. Prepare a configuration file with the identity data. For example:
```json
{
  "x500name": "O=Alice, L=London, C=GB",
  "groupId": "group-1",
  "data": {
    "tlsTenantId": "cluster",
    "sessionKeyTenantId": "alice",
    "tlsCertificates":["-----BEGIN CERTIFICATE-----\nMIIDwDCCAiigAwI...tkIEaQ==\n-----END CERTIFICATE-----\n"],
    "publicKey": "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w...xwIDAQAB\n-----END PUBLIC KEY-----"
  }
}
```
where:
* `tlsTenantId` is the tenant ID under which the TLS key is stored.
* `tlsCertificates` should contain the content of the certificates in PEM format (use either this or `tlsCertificatesFiles`).
* `tlsCertificatesFiles` can be used as an alternative to `tlsCertificates` with a path to a valid certificate file in PEM format.
* `sessionKeyTenantId` is the tenant ID under which the session initiation key is stored.
* `publicSessionKey` is the session initiation public key in PEM format (use either that or `publicSessionKeyFile`).
* `publicSessionKeyFile` can be an alternative to `publicSessionKey` with the path to a file containing the session initiation public key in PEM format.

Then, in order to add the new locally hosted identity, run:
```bash
java -jar applications/tools/p2p-test/p2p-setup/build/bin/corda-p2p-setup*.jar \
  -k broker1:9093 \
  add-identity <path_to_identity_json_file>
```

### Removing a locally hosted identity

To remove a locally hosted identity run the command:
```bash
java -jar applications/tools/p2p-test/p2p-setup/build/bin/corda-p2p-setup*.jar \
  -k broker1:9093 \
  remove-identity -g "group-1" -x "O=Alice, L=London, C=GB"
```

### Creating a membership group
The following instructions can be used to create a membership group.
First:
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
where:
* `networkType` is either `CORDA_5`, `CORDA_4`.
* `protocolModes` are either: `AUTHENTICATED_ENCRYPTION` or `AUTHENTICATION_ONLY`.
* `trustRootCertificates` is the content of the root certificates in PEM format (use either this or `trustRootCertificatesFiles`).
* `trustRootCertificatesFiles` can be an alternative to `trustRootCertificates` with the path to a file containing the trust store certificate in PEM format.

Then, in order to create the group, run:
```bash
java -jar applications/tools/p2p-test/p2p-setup/build/bin/corda-p2p-setup*.jar \
  -k broker1:9093 \
  add-group <path_to_member_json_file>
```

### Removing membership group
To remove a membership group run the command:
```bash
java -jar applications/tools/p2p-test/p2p-setup/build/bin/corda-p2p-setup*.jar \
  -k broker1:9093 \
  remove-group "group-1"
```

### Adding a group member
The following instructions can be used to add a new group member.
First:
1. Make sure you have a valid PEM file containing the public key of the member.
2. Prepare a configuration file with the member data. For example:
```json
{
  "x500name": "O=Alice, L=London, C=GB",
  "groupId": "group-1",
  "data": {
       "publicSessionKey": "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w...xwIDAQAB\n-----END PUBLIC KEY-----",
       "address": "http://alice.com:8085"
  }
}
```
where:
* `address` is the HTTP address of the member (in the format: `http://<host>:<port>`)
* `publicSessionKey` is the session initiation public key in PEM format (use either this or `publicSessionKeyFile`)
* `publicSessionKeyFile` can be an alternative to `publicSessionKey` with the path to a file containing the session initiation public key in PEM format.

Then, in order to add the member, run:
```bash
java -jar applications/tools/p2p-test/p2p-setup/build/bin/corda-p2p-setup*.jar \
  -k broker1:9093 \
  add-member <path_to_member_json_file>
```

Note: to create the public key from the private key, you can use one of the following commands (depending on the key algorithm):
```bash
openssl ec -in private.ec.key -pubout -out public.ec.pem
openssl rsa -in private.rsa.key -pubout > public.rsa.pem
```

### Removing a group member
To remove a membership group run the command:
```bash
java -jar applications/tools/p2p-test/p2p-setup/build/bin/corda-p2p-setup*.jar \
  -k broker1:9093 \
  remove-member -g "group-1" -x "O=Alice, L=London, C=GB"
```

### Publishing configuration for the p2p Gateway.
To publish configuration for the p2p gateway, run:
```bash
java -jar applications/tools/p2p-test/p2p-setup/build/bin/corda-p2p-setup*.jar \
  -k broker1:9093 \
  config-gateway --port=8085
```

Use the command line parameter `--help` to view other configuration options.

### Publishing configuration for the Link Manager.
To publish configuration for the Link Manager, run:
```bash
java -jar applications/tools/p2p-test/p2p-setup/build/bin/corda-p2p-setup*.jar \
  -k broker1:9093 \
  config-link-manager --maxReplayingMessages=200
```

Use the command line parameter `--help` to view other configuration option.


### Kafka servers environment variable
The Kafka connections details can also be provided via the environment variable `KAFKA_SERVERS`, instead of the command line parameter `-k`.
If the `-k <kafka-servers>` command line argument is missing the environment variable `KAFKA_SERVERS` will be used by default.

### Populating a custom topic

Each sub command has a `--topic` option to overwrite the default topic where data will be published for each type of data, unless specified otherwise.

### Running multiple sub commands
One can add multiple sub commands in one run, for example:
```bash
java -jar applications/tools/p2p-test/p2p-setup/build/bin/corda-p2p-setup*.jar \
  -k broker1:9093 \
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

### Applying from a file
One can also save all the setup in a single JSON file, and apply it in one command.
The file should look like the following:
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
    "sessionTimeout": 10000,
    "sessionsPerPeer": 4
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
        "publicKey": "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w...xwIDAQAB\n-----END PUBLIC KEY-----",
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
        "tlsCertificates": ["-----BEGIN CERTIFICATE-----\nMIIDwDCCAiigAwI...tkIEaQ==\n-----END CERTIFICATE-----\n"],
        "publicKey": "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w...xwIDAQAB\n-----END PUBLIC KEY-----"
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
java -jar applications/tools/p2p-test/p2p-setup/build/bin/corda-p2p-setup*.jar \
  -k broker1:9093 \
  apply setup.json \
```