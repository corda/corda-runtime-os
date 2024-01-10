# Kafka Security Tutorial

## Introduction

This guide describes how to setup and enable encryption, authentication and authorization in a local deployment of a kafka cluster with multiple brokers, zookeeper,  consumer client and producer client.

Encryption will be provided via TLS.


Authentication between brokers and zookeeper will be via SASL (DIGEST-MD5).

Authentication between brokers and clients will be via SASL (SCRAM-SHA-256).

Authorization will be provided via Access Control Lists stored in zookeeper.

## 1. Setting up TLS certificates
This step can be skipped. The certificates located in ./certificates can be used locally.

Example usage output of the commands listed below can be found in ./example-logs/key-ceremony-cmds.txt. 

Some commands require user input. e.g values for CommonName (CN). 
Some input requests refer to CN as "What is your first and last name?".
This should always be set to `localhost` for all certificates generated as this will be matched against requests between clients and servers in your local deployment.

### Set up Certificate Authority

Generate a CA that is simply a public-private key pair and certificate, and it is intended to sign other certificates. 

Run the following command from a new dir `ca`
```
openssl req -new -x509 -keyout ca-key -out ca-cert -days 365
```
Output files: `ca-cert`, `ca-key`

Note: the CN for this certificate is not validated and can be set to any value.


### Zookeeper

copy `ca-cert` & `ca-key` files from `ca` dir to `zookeeper` dir

Generate the key and the certificate for each zookeeper in the cluster.
```
keytool -keystore zookeeper.server.keystore.jks -alias zookeeper -validity 365 -genkey -storepass password -keypass password -keyalg RSA
```
output: `zookeeper.server.keystore.jks`



Add the generated CA to zookeepers truststore so that the zookeepers can trust this CA
```
keytool -keystore zookeeper.server.truststore.jks -alias CARoot -importcert -file ca-cert
```
output: zookeeper.server.truststore.jks



Export the certificate from the keystore
```
keytool -keystore zookeeper.server.keystore.jks -alias zookeeper -certreq -file cert-file
```
output: `cert-file`



Sign it with the CA
```
openssl x509 -req -CA ca-cert -CAkey ca-key -in cert-file -out cert-signed -days 365 -CAcreateserial -passin pass:password
```



Import both the certificate of the CA and the signed certificate into the zookeeper keystore:
```
keytool -keystore zookeeper.server.keystore.jks -alias CARoot -importcert -file ca-cert
keytool -keystore zookeeper.server.keystore.jks -alias zookeeper -importcert -file cert-signed
```



### Brokers
The following steps should be completed for each broker in the cluster in separate directories. Replace `brokerN` with `broker1`/`broker2`/`broker3` for each step.

copy `ca-cert` & `ca-key` files from `ca` dir to `brokerN` dir

Generate the key and the certificate for each Kafka broker in the cluster. 
```
keytool -keystore kafka.server.keystore.jks -alias brokerN -validity 365 -genkey -storepass password -keypass password -keyalg RSA
```
output: `kafka.server.keystore.jks`



Add the generated CA to the brokers’ truststore so that the brokers can trust this CA
```
keytool -keystore kafka.server.truststore.jks -alias CARoot -importcert -file ca-cert
```
output: kafka.server.truststore.jks



Export the certificate from the keystore
```
keytool -keystore kafka.server.keystore.jks -alias brokerN -certreq -file cert-file
```
output: `cert-file`



Sign it with the CA
```
openssl x509 -req -CA ca-cert -CAkey ca-key -in cert-file -out cert-signed -days 365 -CAcreateserial -passin pass:password
```



Import both the certificate of the CA and the signed certificate into the broker keystore:
```
keytool -keystore kafka.server.keystore.jks -alias CARoot -importcert -file ca-cert
keytool -keystore kafka.server.keystore.jks -alias brokerN -importcert -file cert-signed
```



### Consumer and Producer Clients

Add the generated CA to the clients truststore so that the clients can trust this CA.
This will be shared by the consumer and producer clients for the purpose of this setup.
```
keytool -keystore kafka.client.truststore.jks -alias CARoot -importcert -file ca-cert
```
output: `kafka.client.truststore.jks`




## 2. Config files

### Kafka

#### Kafka Server Properties

Example kafka broker server configurations are available in the ./kafka-configs/serverN.properties

The main properties different to the default settings are listed and described below:
```
#Host name verification of servers is enabled by default for client connections as well as inter-broker connections to prevent man-in-the-middle attacks. 
#Server host name verification may be disabled by setting the below
ssl.endpoint.identification.algorithm=

#listeners hostname MUST MATCH certificate CN
listeners=SASL_SSL://localhost:9094
#define which protocol to use for inter boker communication. maps to listener above
security.inter.broker.protocol=SASL_SSL

#Paths to keystore and truststore
ssl.keystore.location=C:/kafka-security-demo/brokerN/kafka.server.keystore.jks
ssl.keystore.password=password
ssl.key.password=password
ssl.truststore.location=C:/kafka-security-demo/brokerN/kafka.server.truststore.jks
ssl.truststore.password=password

#Enable SASL mechanism
sasl.enabled.mechanisms=SCRAM-SHA-256
#define which protocol mechansim to use for inter-broker communication.
sasl.mechanism.inter.broker.protocol=SCRAM-SHA-256

#SASL account details used by this broker for authentication via SASL (SCRAM-SHA-256)
listener.name.sasl_ssl.scram-sha-256.sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required \
  username="kafkabroker" \
  password="password";
   
#To enable ACLs, we need to configure an authorizer class to be used. Kafka provides a simple authorizer implementation
authorizer.class.name=kafka.security.auth.SimpleAclAuthorizer
#authenticated super users are granted authorization to all resources without checking ACLs. Set the brokers users as super to simplify inter broker communication
super.users=User:kafkabroker

# Connect to the ZooKeeper port configured for TLS
zookeeper.connect=localhost:2182
# Required to use TLS to ZooKeeper (default is false)
zookeeper.ssl.client.enable=true
# Required to use TLS to ZooKeeper
zookeeper.clientCnxnSocket=org.apache.zookeeper.ClientCnxnSocketNetty
# Define trust store to use TLS to ZooKeeper; ignored unless zookeeper.ssl.client.enable=true
zookeeper.ssl.truststore.location=C:/kafka-security-demo/brokerN/kafka.server.truststore.jks
zookeeper.ssl.truststore.password=password

#Disable auto create of topics
auto.create.topics.enable=false
```



#### Kafka JAAS Config

The credentials kafka uses to authenticate against zookeeper is stored in a JAAS config file.

```
Client {
   org.apache.zookeeper.server.auth.DigestLoginModule required
   username="kafka"
   password="password";
};
```



### Zookeeper

#### Zookeeper server config

Example zookeeper server configurations are available in the ./kafka-configs/zookeeper.properties

The main zookeeper properties different to the default settings are listed below:
```
#unsecure port at which the clients will connect, left open to simplify cmd line interaction locally, not used by brokers
clientPort=2181
#secure port for SSL traffic
secureClientPort=2182
#class to use for authentication
authProvider.sasl=org.apache.zookeeper.server.auth.SASLAuthenticationProvider
#class to use for SSL
serverCnxnFactory=org.apache.zookeeper.server.NettyServerCnxnFactory

#keystore/truststore
ssl.keyStore.location=C:/kafka-security-demo/zookeeper/zookeeper.server.keystore.jks
ssl.keyStore.password=password
ssl.trustStore.location=C:/kafka-security-demo/zookeeper/zookeeper.server.truststore.jks
ssl.trustStore.password=password

#When set to none, ZooKeeper allows clients to connect using a TLS-encrypted connection without presenting their own CA certificate (disables 2way mTLS as SASL is used instead)
ssl.clientAuth=none
```



#### Zookeeper JAAS config

A JAAS configuration file is necessary to store the client login details that are accepted by this zookeeper.

The kafka brokers will use these credentials to authenticate. Zookeeper only supports PLAIN and DIGEST-MD5 authentication.

These login details match the JAAS file used by the kafka brokers.

Both of these mechanisms are not considered cryptographically secure and as such it is advised zookeeper servers to be placed on private networks secured by network rules in a production environment.

```
Server {
    org.apache.zookeeper.server.auth.DigestLoginModule required
    user_kafka="password";
};
```


### Clients

To simplify local setup both consumer and producer client share the same truststore.

The consumer and producer will use a different username to connect to the cluster.

Each user will be granted different permissions to the TOPIC and GROUP resources later.

Both will use SASL (SCRAM-SHA-256) authentication with the cluster.

Sample files provided in ./kafka-configs/consumerClient.properties and ./kafka-configs/producerClient.properties



#### Producer Client

```
security.protocol=SASL_SSL
ssl.truststore.location=C:/kafka-security-demo/client/kafka.client.truststore.jks
ssl.truststore.password=password
sasl.mechanism=SCRAM-SHA-256
sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required \
    username="producerClient" \
    password="password";
```


#### Consumer Client

```
security.protocol=SASL_SSL
ssl.truststore.location=C:/kafka-security-demo/client/kafka.client.truststore.jks
ssl.truststore.password=password
sasl.mechanism=SCRAM-SHA-256
sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required \
    username="consumerClient" \
    password="password";
```

## Running and configuring the cluster

### Start Zookeeper and save SCRAM details
Sample output can be seen in ./example-logs/zookeeper-cmds-and-output.txt


Point zookeeper at its JAAS configuration file which stores the login details for this server.

Start zookeeper 
```
set KAFKA_OPTS=-Djava.security.auth.login.config=c:/dev/kafka/config/zookeeper_jaas.conf
zookeeper-server-start.bat ../../config/zookeeper.properties
```

SCRAM authentication credentials used by brokers and broker clients are stored in zookeeper. For convenience the unsecure port is used to connect to zookeeper from the command line.
The `--zookeeper` must be used instead of `--bootstrap-server`. Ensure `KAFKA_OPTS` is set before executing kafka-configs commands.
```
set KAFKA_OPTS=-Djava.security.auth.login.config=c:/dev/kafka/config/zookeeper_jaas.conf
kafka-configs.bat --zookeeper localhost:2181 --alter --add-config SCRAM-SHA-256=[iterations=8192,password=password],SCRAM-SHA-512=[password=password] --entity-type users --entity-name kafkabroker
kafka-configs.bat --zookeeper localhost:2181 --alter --add-config SCRAM-SHA-256=[iterations=8192,password=password],SCRAM-SHA-512=[password=password] --entity-type users --entity-name consumerClient
kafka-configs.bat --zookeeper localhost:2181 --alter --add-config SCRAM-SHA-256=[iterations=8192,password=password],SCRAM-SHA-512=[password=password] --entity-type users --entity-name producerClient
```

If you do not save the SCRAM user login details to zookeeper the error in the broker logs will show:
```
INFO [Controller id=1, targetBrokerId=1] Failed authentication with localhost/127.0.0.1 (Authentication failed during authentication due to invalid credentials with SASL mechanism SCRAM-SHA-256) (org.apache.kafka.common.network.Selector)
```

### Start Kafka Brokers
Sample output can be seen in ./example-logs/brokerN-cmds-and-output.txt


Point each broker to the kafka server JAAS file and start it.

```
set KAFKA_OPTS=-Djava.security.auth.login.config=c:/dev/kafka/config/kafka_server_jaas.conf
kafka-server-start.bat ../../config/server1.properties


set KAFKA_OPTS=-Djava.security.auth.login.config=c:/dev/kafka/config/kafka_server_jaas.conf
kafka-server-start.bat ../../config/server2.properties


set KAFKA_OPTS=-Djava.security.auth.login.config=c:/dev/kafka/config/kafka_server_jaas.conf
kafka-server-start.bat ../../config/server3.properties
```

### Configure topic and ACLs

Sample output can be seen in ./example-logs/zookeeper-save-SCRAM-login-create-topic-save-ACLs.txt


Create topic
```
kafka-topics.bat --zookeeper localhost:2181 --create --topic kafka-security-topic --replication-factor 3 --partitions 3
```

#### Create ACLs for producerClient and consumerClient.

The following command will grant the producerClient `WRITE`, `CREATE` and `DESCRIBE` on the topic `kafka-security-topic`.
This will allow this client to produce to the topic, create topics, add meta-data and get offsets.
Again ensure KAFKA_OPTS is set before running kafka-acls commands.
```
set KAFKA_OPTS=-Djava.security.auth.login.config=c:/dev/kafka/config/zookeeper_jaas.conf
kafka-acls.bat --authorizer-properties zookeeper.connect=localhost:2181 --add --allow-principal User:producerClient --producer --topic kafka-security-topic
```

This command will grant `READ` and `DESCRIBE` on the topic `kafka-security-topic`.
It will also grant `READ` on the consumer group `kafka-security-group`.
This will allow this client to join the group and read from the topic.
```
kafka-acls.bat --authorizer-properties zookeeper.connect=localhost:2181 --add --allow-principal User:consumerClient --consumer --topic kafka-security-topic --group kafka-security-group
```

### Start the consumer and producer clients

Sample output can be seen in ./example-logs/consumer-and-producer-client-logs.txt


Start producer
```
kafka-console-producer.bat --broker-list localhost:9094 --topic kafka-security-topic --producer.config ../../config/producerClient.properties
```

Start consumer
```
kafka-console-consumer.bat --bootstrap-server localhost:9094 --topic kafka-security-topic --group kafka-security-group --consumer.config ../../config/consumerClient.properties
```

The producer window should allow for text input and the data entered should be outputted in the consumer window.

If the certificates have not been set up the clients will output an AuthenticationException.

If the ACLs have not been configured correctly the clients may output an AuthorizationException.

# Additional Reading
- http://kafka.apache.org/documentation.html#security
- https://docs.confluent.io/platform/current/security/security_tutorial.html
- https://docs.confluent.io/platform/current/kafka/authentication_sasl/index.html
- https://docs.confluent.io/platform/current/kafka/authentication_sasl/authentication_sasl_scram.html
- https://docs.confluent.io/platform/current/kafka/incremental-security-upgrade.html
- https://cwiki.apache.org/confluence/display/ZOOKEEPER/ZooKeeper+SSL+User+Guide
- https://docs.confluent.io/platform/current/security/zk-security.html
- https://docs.confluent.io/platform/current/kafka/authorization.html
- https://jaceklaskowski.gitbooks.io/apache-kafka/content/kafka-admin-AclCommand.html
- https://jaceklaskowski.gitbooks.io/apache-kafka/content/kafka-demo-acl-authorization.html
