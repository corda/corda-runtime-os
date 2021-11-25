A set of scripts to crate a p2p cluster.

# Before you begin
1. Install kubectl (see [here](https://kubernetes.io/docs/tasks/tools/))
2. Install minikube (see [here](https://minikube.sigs.k8s.io/docs/start/))
3. Install Telepresence (see [here](https://www.telepresence.io/docs/latest/install/))
4. Start minikube:
```bash
minikube start -n 4 --memory 4096
```
5. Make sure that the `CORDA_ARTIFACTORY_USERNAME` and `CORDA_ARTIFACTORY_PASSWORD` environment variables are set to your [Artifactory credentials](https://software.r3.com/ui/admin/artifactory/user_profile).
6. Connect to Telepresence:
```bash
telepresence connect 
```
7. Build the app:
```bash
./gradlew :applications:tools:p2p-test:p2p-layer-deployment:install
```
8. Login into [TinyCert](https://www.tinycert.org/login) and make sure you have those Make sure that you have those environment variables:
  * `TINYCERT_API_KEY` the API key (You can get it [here](https://www.tinycert.org/docs/api))
  * `TINYCERT_PASS_PHRASE` Your pass phrase.
  * `TINYCERT_EMAIL` Your email address as it's registered in TinyCert.

# Using the application
## Deploying a new cluster
To deploy a new cluster run:
```bash
./applications/tools/p2p-test/p2p-layer-deployment/build/install/p2p-layer-deployment/bin/p2p-layer-deployment deploy [-n <namespace>]
```
This will deploy a cluster named `p2p-layer` on you minikube (it will destroy any running cluster with that name). 
Some additional options are:
```
  -d, --dry-run              Just output the yaml the stdout, do not interact
                               with K8s
      --db-init-sql-file=<sqlInitFile>
                             A file name with the initial SQL to create the
                               databases
      --db-password=<dbPassword>
                             The database password
      --db-username=<dbUsername>
                             The database username
  -g, --gateway-count=<gatewayCount>
                             Number of Gateways in the cluster
                             Number of Gateways in the cluster
      --group-id=<groupId>   The group ID
  -H, --hosts=<hostsNames>   The hosts names
  -k, --kafka-brokers=<kafkaBrokerCount>
                             Number of kafka brokers in the cluster
  -l, --link-manager-count=<linkManagerCount>
                             Number of Link Managers in the cluster
  -n, --name=<namespaceName> The name of the namespace
      --no-volume-creation   Avoid creating any volumes
      --storage-class=<storageClassName>
                             The storage class name
  -t, --tag=<tag>            The docker name of the tag to pull
  -x, --x500-name=<x500Name> The X 500 name
  -z, --zoo-keepers-count=<zooKeeperCount>
                             Number of Zoo Keepers in the cluster
```

## Destroying a cluster
To delete a cluster run the command:
```bash
./applications/tools/p2p-test/p2p-layer-deployment/build/install/p2p-layer-deployment/bin/p2p-layer-deployment destroy
```
With the `-n` option to change the name of the cluster to destroy (default to `p2p-layer`). Use `--all` to destroy all the p2p clusters

## Accessing bash of a pod in a running cluster
To access one of the pods, use the command:
```bash
./applications/tools/p2p-test/p2p-layer-deployment/build/install/p2p-layer-deployment/bin/p2p-layer-deployment bash -p <pod> -n <namespace> [-- command to run]
```
For example:
```bash
./applications/tools/p2p-test/p2p-layer-deployment/build/install/p2p-layer-deployment/bin/p2p-layer-deployment bash -p p2p-gateway-2 -- sh
```
## Accessing to the PostGreSQL Client
To access to the database client use:
```bash
./applications/tools/p2p-test/p2p-layer-deployment/build/install/p2p-layer-deployment/bin/p2p-layer-deployment psql [-n <namespace>] 
```

## View logs 
To view logs of pods in the cluster use:
```bash
./applications/tools/p2p-test/p2p-layer-deployment/build/install/p2p-layer-deployment/bin/p2p-layer-deployment logs [-n <namespace>] [-p <pod-regex>]
```

## JDBC connection
To forward the DB port and access it from the local host use:
```bash
./applications/tools/p2p-test/p2p-layer-deployment/build/install/p2p-layer-deployment/bin/p2p-layer-deployment jdbc [-n <namespace>]
```

## Create custom key store
To create custom key store (that can be used for the gateway configuration) use the `create-stores` command. That is, something like:
```bash
./applications/tools/p2p-test/p2p-layer-deployment/build/install/p2p-layer-deployment/bin/p2p-layer-deployment \
create-stores \
-h <host-name> -h <another-host-name>
```
This will generate a key store and a trust store file. Additional arguments:
```
  -e, --tinycert-email=<email>
                       The TinyCert email
  -h, --host=<hosts>   The host names
  -k, --tinycert-api-key=<apiKey>
                       The TinyCert API Key
      --key-store-password=<keyStorePassword>
                       The key store password
  -p, --tinycert-passphrase=<passPhrase>
                       The TinyCert Pass phrase
  -s, --ssl-store=<sslStoreFile>
                       The SSL store file
  -t, --trust-store=<trustStoreFile>
                       The trust store file
      --trust-store-password=<trustStorePassword>
                       The trust store password
```

## Configuring
To configure the gateway and link manager we can use the [Configuration publisher](../configuration-publisher/README.md).
1. Build the tool:
```bash
./gradlew :applications:tools:p2p-test:configuration-publisher:clean :applications:tools:p2p-test:configuration-publisher:appJar
```
2. Make sure Telepresence is connected:
```bash
telepresence connect 
``` 
3. Run the config tool with `--kafka-servers kafka-broker-1.p2p-layer:9093`. 

### Gateway Example:
```bash
java \
-jar ./applications/tools/p2p-test/configuration-publisher/build/bin/corda-configuration-publisher-5.0.0.0-SNAPSHOT.jar \
--kafka-servers kafka-broker-1.p2p-layer:9093 \
gateway \
--keyStore ./components/gateway/src/integration-test/resources/sslkeystore_alice.jks \
--trustStore ./components/gateway/src/integration-test/resources/truststore.jks \
--port 80 \
--host www.alice.net
```
Please note, the port number need to be 80, the host name should be one of the host names from the deploy (default to www.alice.net).

### Link Manager Example:
```bash
java \
-jar ./applications/tools/p2p-test/configuration-publisher/build/bin/corda-configuration-publisher-5.0.0.0-SNAPSHOT.jar \
--kafka-servers kafka-broker-1.p2p-layer:9093 \
linkmanager \
--locallyHostedIdentity=O=Alice,L=London,C=GB:group1
```

## Starting the simulator
### Receiver
To start the receiver run:
```bash
./applications/tools/p2p-test/p2p-layer-deployment/build/install/p2p-layer-deployment/bin/p2p-layer-deployment receive [-n <namespace>] 
```

### DB-Sink
To start the DB sink:
```bash
./applications/tools/p2p-test/p2p-layer-deployment/build/install/p2p-layer-deployment/bin/p2p-layer-deployment sb-sink [-n <namespace>] 
```

### Sender
To start the DB sink:
```bash
./applications/tools/p2p-test/p2p-layer-deployment/build/install/p2p-layer-deployment/bin/p2p-layer-deployment send [-n <namespace>] 
```
where the load generation parameters can be set via the arguments:
```
  -b, --batch-size=<batchSize>
                        size of batch
  -d, --delay=<delay>   delay in milliseconds
  -f, --one-off         One off generation type
  -n, --name=<namespaceName>
                        The name of the namespace
  -o, --our=<our>       Our peer X500 name and group ID (<x500name>:<groupId>)
  -p, --peer=<peer>     The peer X500 name and group ID (<x500name>:<groupId>)
  -s, --message-size-bytes=<messageSizeBytes>
                        size message in bytes
  -t, --total-number-of-messages=<totalNumberOfMessages>
                        Total number of messages (for one ofe case)
```

# After you finish
1. Destroy the cluster.
2. Stop and uninstall Telepresence:
```bash
telepresence quit && telepresence uninstall --everything
```
3. Stop and delete minikube:
```
minikube stop && minikube delete
```
