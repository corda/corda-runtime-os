# Flow Worker Setup
This tool is designed as a throw away tool for driving the flow processing components during dev and debug. It is not 
intended as fully featured test or setup tool.

The purpose of this tool is to run one or more pre-defined, ordered tasks that can publish messages to kafka.

each task is defined as a simple class with access to a context that allows basic kafka operations such as create/delete
topics or publish messages.

the current version assumes kafka is running on localhost:9092

## Tasks
- *DeleteAllTopics* - deletes all topics on the kafka instance
- *CreateTopics* - creates all the topics needed to run the flow and associated components
- *PublishConfig* - publishes configurations sections from a file to the config topic. The file is passed in via the
  `--config` argument
- *SetupVirtualNode* - Publishes the vNode, CPI and CPK meta data onto Kafka. The `--cpiDir` specifies the folder where 
  the CBP is located. There is also an optional parameter `--cpiDockerDir` that will change the config for where the flow 
  worker will look for the CPI information.
- *StartFlow* - Publishes a Start Flow event to kafka, at the moment this is hard coded to the hello world example app

## Usage
to run the app specify a list of one or more of the tasks as command args + any optional args required by specific tasks
e.g.

Delete topics only  
`java -jar build/bin/corda-flow-worker-setup-5.0.0.0-SNAPSHOT.jar DeleteAllTopics`

Full restart  
`java -jar build/bin/corda-flow-worker-setup-5.0.0.0-SNAPSHOT.jar DeleteAllTopics CreateTopics SetupVirtualNode
PublishConfig StartFlow --cpiDir C:/ows/git-repo/corda-runtime-os-build/testing/cpbs/helloworld/build/libs
--config config.conf
`
Note the absolute path. Update it to reflect your own as relative causes NPE

## Link Manager setup

To publish the configuration required by the `LinkManager` which is used by the `FlowWorker` application, add the config below to a JSON file (e.g. `config.json`):

```json
{
  "linkManagerConfig": {
    "maxMessageSize": 1000000,
    "maxMessages": 100,
    "ConstantReplayAlgorithm": {
      "replayPeriod": 2000
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
      "groupId": "flow-worker-dev",
      "data": {
        "networkType": "CORDA_5",
        "protocolModes": ["AUTHENTICATION_ONLY", "AUTHENTICATED_ENCRYPTION"],
        "trustRootCertificates":["-----BEGIN CERTIFICATE-----\nMIIELzCCAxegAwIBAgIBADANBgkqhkiG9w0BAQsFADB6MQswCQYDVQQGEwJHQjEP\nMA0GA1UEBwwGTG9uZG9uMRQwEgYDVQQKDAtCb2dkYW4gVGVzdDErMCkGA1UECwwi\nU2VjdXJlIERpZ2l0YWwgQ2VydGlmaWNhdGUgU2lnbmluZzEXMBUGA1UEAwwOQm9n\nZGFuIFRlc3QgQ0EwHhcNMjEwNzEyMDcxMDEyWhcNMzEwNzEwMDcxMDEyWjB6MQsw\nCQYDVQQGEwJHQjEPMA0GA1UEBwwGTG9uZG9uMRQwEgYDVQQKDAtCb2dkYW4gVGVz\ndDErMCkGA1UECwwiU2VjdXJlIERpZ2l0YWwgQ2VydGlmaWNhdGUgU2lnbmluZzEX\nMBUGA1UEAwwOQm9nZGFuIFRlc3QgQ0EwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAw\nggEKAoIBAQDUerOx5RYuGsRztkcexy/zVJXeIlTJtXkWSmTZEEuXksyp7mEbZHLI\nqHHOlV/KQtKDOSt8Roe15C/H9L7gKU6TmU2PhOj8d/g+l/UXSnCRx5VxjOBV0B4n\naLmjFTprszW4h1bplrYRCPdXkguSGdVjsFPem4Cl28+EzRAD8psixwKC0ZHs2tFc\n46owtKtHZbJ2d8bzbITShM9X6ENN7BofMkdYPJHSbXKHKPxvFQwnjlLoYnib+Obr\n7tPMeILoG6tJqBU/ICD19ic8TVV0d9og9Q2vKV6BL2cNeqwNA1/YQbhqdSA0ubPc\neLcMsdMcRfhEuIAryY3keldrU66dnS3RAgMBAAGjgb8wgbwwDwYDVR0TAQH/BAUw\nAwEB/zAdBgNVHQ4EFgQU3222Ezr8XGX9kEF7eGmXg04b1SowDgYDVR0PAQH/BAQD\nAgGuMDQGA1UdHwQtMCswKaAnoCWGI2h0dHA6Ly9jcmwudGlueWNlcnQub3JnL2Nh\nLTkwNzEuY3JsMCEGA1UdEQQaMBiBFmJvZ2Rhbi5wYXVuZXNjdUByMy5jb20wIQYD\nVR0SBBowGIEWYm9nZGFuLnBhdW5lc2N1QHIzLmNvbTANBgkqhkiG9w0BAQsFAAOC\nAQEABg1Mj7jjyLavrEG/GpZDbatI09ScpEdfNtUg89BAWxfB0V8ItvNjePPQeMCs\nXWcDtiI14xOjNcGndEbSLJLW4oaymK9G7LkK//VvoZ/0Ostfs8sOEuxgT+MLbUWV\nq9/q2+JphnZV10h9LWLU4wDILyNaGiQi9x3NGcqfUYR+KC0IJBOlLnKq1uHmdvJh\naVEWAa4dKt/wSE8Vh9/OvaXUIMDxh6PHFc6t9Pkca/8Nu/X9Sep9Vsj2E4yairnb\nDcgQK4YagUkOtOaRf7ccGPOQRKzryfWNetgLdpKzxghPxAa1NE5SCvfR1xB1ViZj\nCndMm6VhBwuhc1i4XiT4UDgr0g==\n-----END CERTIFICATE-----\n"]
      }
    }
  ],
  "membersToAdd": [
    {
      "x500name": "CN=Alice, O=Alice Corp, L=LDN, C=GB",
      "groupId": "flow-worker-dev",
      "data": {
        "address": "http://alice.com:8085",
        "publicSessionKey": "-----BEGIN PUBLIC KEY-----\nMIGeMA0GCSqGSIb3DQEBAQUAA4GMADCBiAKBgGh9cScVJJ4jHzLfla5cVYTjz4VN\n/cIdjgyxL/56HKfeF2mCdbHAUtkaoua7H1AjtsbE1paB20mqjjzjb32zPRfDU5OE\nHniSJnGdJ0ZkcWSgbwcWudDyC7fowO9YyPdAlGaZKRrAL2O4OdfRHBWUk0NNZ0pk\n416Nyo8m8gGiienhAgMBAAE=\n-----END PUBLIC KEY-----"
      }
    },
    {
      "x500name": "CN=Bob, O=Bob Corp, L=LDN, C=GB",
      "groupId": "flow-worker-dev",
      "data": {
        "address": "http://alice.com:8085",
        "publicSessionKey": "-----BEGIN PUBLIC KEY-----\nMIGeMA0GCSqGSIb3DQEBAQUAA4GMADCBiAKBgGh9cScVJJ4jHzLfla5cVYTjz4VN\n/cIdjgyxL/56HKfeF2mCdbHAUtkaoua7H1AjtsbE1paB20mqjjzjb32zPRfDU5OE\nHniSJnGdJ0ZkcWSgbwcWudDyC7fowO9YyPdAlGaZKRrAL2O4OdfRHBWUk0NNZ0pk\n416Nyo8m8gGiienhAgMBAAE=\n-----END PUBLIC KEY-----"
      }
    }
  ],
  "identitiesToAdd": [
    {
      "x500name": "CN=Alice, O=Alice Corp, L=LDN, C=GB",
      "groupId": "flow-worker-dev",
      "data": {
        "tlsTenantId": "cluster",
        "sessionKeyTenantId": "alice",
        "tlsCertificates":["-----BEGIN CERTIFICATE-----\nMFkwEwYHK+B3YGgcIALw==\n-----END CERTIFICATE-----\n"],
        "publicSessionKey": "-----BEGIN PUBLIC KEY-----\nMIGeMA0GCSqGSIb3DQEBAQUAA4GMADCBiAKBgGh9cScVJJ4jHzLfla5cVYTjz4VN\n/cIdjgyxL/56HKfeF2mCdbHAUtkaoua7H1AjtsbE1paB20mqjjzjb32zPRfDU5OE\nHniSJnGdJ0ZkcWSgbwcWudDyC7fowO9YyPdAlGaZKRrAL2O4OdfRHBWUk0NNZ0pk\n416Nyo8m8gGiienhAgMBAAE=\n-----END PUBLIC KEY-----"
      }
    },
    {
      "x500name": "CN=Bob, O=Bob Corp, L=LDN, C=GB",
      "groupId": "flow-worker-dev",
      "data": {
        "tlsTenantId": "cluster",
        "sessionKeyTenantId": "alice",
        "tlsCertificates":["-----BEGIN CERTIFICATE-----\nMIIDwDCCAiigAwI...tkIEaQ==\n-----END CERTIFICATE-----\n"],
        "publicSessionKey": "-----BEGIN PUBLIC KEY-----\nMIGeMA0GCSqGSIb3DQEBAQUAA4GMADCBiAKBgGh9cScVJJ4jHzLfla5cVYTjz4VN\n/cIdjgyxL/56HKfeF2mCdbHAUtkaoua7H1AjtsbE1paB20mqjjzjb32zPRfDU5OE\nHniSJnGdJ0ZkcWSgbwcWudDyC7fowO9YyPdAlGaZKRrAL2O4OdfRHBWUk0NNZ0pk\n416Nyo8m8gGiienhAgMBAAE=\n-----END PUBLIC KEY-----"
      }
    }
  ],
  "keysToAdd": [
    {
      "keys": "-----BEGIN RSA PRIVATE KEY-----\nMIICWwIBAAKBgGh9cScVJJ4jHzLfla5cVYTjz4VN/cIdjgyxL/56HKfeF2mCdbHA\nUtkaoua7H1AjtsbE1paB20mqjjzjb32zPRfDU5OEHniSJnGdJ0ZkcWSgbwcWudDy\nC7fowO9YyPdAlGaZKRrAL2O4OdfRHBWUk0NNZ0pk416Nyo8m8gGiienhAgMBAAEC\ngYBcAFdo+gzL0FDgEk1QwKvr3koSLaGJEUzJkBmaDxq8E6i5lczbPWO1FObqEUh4\n33lHenkW/C+ApVOn+PlpzC+1XlV9TEhpFgrlikxgSwKNDen1RozB5pqsNbR0Y7+P\nPU53l1/KuqDfcmxW/mKf89Dwip9QVTm/6+gQzR3R85nrgQJBAMlt3Sg6GNjeVSYH\nforPl6B1lXjcxJvZiJJAhrEAcjm4iypAN9bU2/LHrB8+/EfQL/HTqFKFSP0tJBAL\n+uezbjUCQQCEzFr15lZujfFI3sjGj3ylJ/WALv76HGFzPyqLX2FxV+njSftRpagW\n4dIRFIImCW+VkgXah7p3++AwNceeo/J9AkEAlmMiNUB7AJ+ubwA1aCnpiPGBMYWS\nfNGC976ZUVmF7rZroJXlms29kZumVIXQEUXMJf7iswm0HuIvBJQNNiRu6QJAFkkN\nArMS0h6z3Ry16MxviG+6PTalx2c321VAWH87JQAx2diyejMokB55WDBu3t86gIku\nvkuEuVnld3Gu/CpbKQJAZHLJnFJYtfGKe0+Anrib54Mctqq43LkOTivcRobh8E73\nl5puj59QaPAytBEXotDHoFGiAhMBRgWupLQEWgAzdg==\n-----END RSA PRIVATE KEY-----\n",
      "tenantId": "tenant-alice-1",
      "publishAlias": "alice-identity-key"
    },
    {
      "keys": "-----BEGIN RSA PRIVATE KEY-----\nMIICWwIBAAKBgGh9cScVJJ4jHzLfla5cVYTjz4VN/cIdjgyxL/56HKfeF2mCdbHA\nUtkaoua7H1AjtsbE1paB20mqjjzjb32zPRfDU5OEHniSJnGdJ0ZkcWSgbwcWudDy\nC7fowO9YyPdAlGaZKRrAL2O4OdfRHBWUk0NNZ0pk416Nyo8m8gGiienhAgMBAAEC\ngYBcAFdo+gzL0FDgEk1QwKvr3koSLaGJEUzJkBmaDxq8E6i5lczbPWO1FObqEUh4\n33lHenkW/C+ApVOn+PlpzC+1XlV9TEhpFgrlikxgSwKNDen1RozB5pqsNbR0Y7+P\nPU53l1/KuqDfcmxW/mKf89Dwip9QVTm/6+gQzR3R85nrgQJBAMlt3Sg6GNjeVSYH\nforPl6B1lXjcxJvZiJJAhrEAcjm4iypAN9bU2/LHrB8+/EfQL/HTqFKFSP0tJBAL\n+uezbjUCQQCEzFr15lZujfFI3sjGj3ylJ/WALv76HGFzPyqLX2FxV+njSftRpagW\n4dIRFIImCW+VkgXah7p3++AwNceeo/J9AkEAlmMiNUB7AJ+ubwA1aCnpiPGBMYWS\nfNGC976ZUVmF7rZroJXlms29kZumVIXQEUXMJf7iswm0HuIvBJQNNiRu6QJAFkkN\nArMS0h6z3Ry16MxviG+6PTalx2c321VAWH87JQAx2diyejMokB55WDBu3t86gIku\nvkuEuVnld3Gu/CpbKQJAZHLJnFJYtfGKe0+Anrib54Mctqq43LkOTivcRobh8E73\nl5puj59QaPAytBEXotDHoFGiAhMBRgWupLQEWgAzdg==\n-----END RSA PRIVATE KEY-----\n",
      "tenantId": "tenant-bob-1",
      "publishAlias": "bob-identity-key"
    }
  ]
}
```

Then compile the `p2p-setup` tool:

```shell
gradlew applications:tools:p2p-test:p2p-setup:appJar
```

Then run the `p2p-setup` tool (ran from `applications/tools/p2p-test/p2p-setup`):

```shell
java -jar build/bin/corda-p2p-setup-5.0.0.0-SNAPSHOT.jar -k localhost:9092 apply setup.json
```

If the Link Manager setup doesn't work or goes out of date, refer to `applications/tools/p2p-test/p2p-setup/README.md` instead.