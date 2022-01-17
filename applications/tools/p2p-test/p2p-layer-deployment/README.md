A tool that can be used to deploy all the p2p components in a Kubernetes cluster.

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
8. Login into [TinyCert](https://www.tinycert.org/login) and make sure you have those environment variables:
  * `TINYCERT_API_KEY` the API key (You can get it [here](https://www.tinycert.org/docs/api))
  * `TINYCERT_PASS_PHRASE` Your pass phrase.
  * `TINYCERT_EMAIL` Your email address as it's registered in TinyCert.

# Using the application
Throughout this section we will use the command `p2p-layer-deployment` as a substitute to the full path -
```bash
./applications/tools/p2p-test/p2p-layer-deployment/build/install/p2p-layer-deployment/bin/p2p-layer-deployment
```


## Deploying a new cluster
To deploy a new cluster run:
```bash
p2p-layer-deployment deploy -n <namespace>
```
This will deploy a cluster named `<namespace>` on you minikube (it will destroy any running cluster with that name). 
It will also configure the gateway and link manager.
To view additional options, run 
```bash
p2p-layer-deployment deploy --help
```
### Accessing to Kafka UI in a deployed cluster
By default, a Kafka UI is accessible using http://kafka-ui.<namespace>/ from the browser.   If you want to disable it, you can use the flag `--disable-kafka-ui`.

### Debugging a process
If the `--debug` flag was set, one can debug the gateway/link manager with port 8002. The hos name is the format `app.namespace`, for example, to debug the first gateway in namespace `sender` use host `p2p-gateway-1.sender`

## Destroying a cluster
To delete a cluster named `namespace` run the command:
```bash
p2p-layer-deployment destroy -n namespace
```

To delete all the clusters you have created run the command
```bash
p2p-layer-deployment destroy --all
```


## Accessing bash of a pod in a running cluster
To access one of the pods, use the command:
```bash
p2p-layer-deployment bash -p <pod> -n <namespace> [-- command to run]
```
For example:
```bash
p2p-layer-deployment bash -p p2p-gateway-2 -n test-sender -- sh
```

## View logs
To view logs of pods in the cluster use:
```bash
p2p-layer-deployment logs -n <namespace> [-p <pod-regex>]
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
-jar ./applications/tools/p2p-test/p2p-configuration-publisher/build/bin/corda-configuration-publisher-5.0.0.0-SNAPSHOT.jar \
--kafka-servers kafka-broker-1.p2p-layer:9093 \
gateway \
--keyStore ./components/gateway/src/integration-test/resources/sslkeystore_alice.jks \
--trustStore ./components/gateway/src/integration-test/resources/truststore.jks \
--port 1433 \
```
Please note, the port number need to be 1433, the host name should be one of the host names from the deploy (default to www.alice.net).

### Link Manager Example:
```bash
java \
-jar ./applications/tools/p2p-test/p2p-configuration-publisher/build/bin/corda-configuration-publisher-5.0.0.0-SNAPSHOT.jar \
--kafka-servers kafka-broker-1.p2p-layer:9093 \
linkmanager \
--locallyHostedIdentity=O=Alice,L=London,C=GB:group1
```

## Managing a the simulator
Use the `simulator` command to manage the simulator application.
### Managing the simulator databases
The simulator needs a database to save the messages it has sent and received. This can be manage by the `simulator db` command.

#### Starting a new database
Run the command:
```bash
p2p-layer-deployment simulator db start [-n <db-namespace>]
```
To create a new namespace named `<db-namespace>` with a database pod. By default, the name of the database namespace will be your name with `p2p-db2` postfix.
Use `--help` to view more option on setting up a database.

#### Accessing to the PostGreSQL Client
To access to the database client use:
```bash
p2p-layer-deployment simulator db psql [-n <db-namespace>] 
```

#### JDBC connection
To view the JDBC connection URL use:
```bash
p2p-layer-deployment simulator db jdbc [-n <db-namespace>]
```

#### View database status
To view the database status use:
```bash
p2p-layer-deployment simulator db status [-n <db-namespace>] 
```

#### Kill running database
To stop a running database use:
```bash
p2p-layer-deployment simulator db stop [-n <db-namespace>] 
```

### Managing the simulator receivers
The simulator needs to simulate something to receive the messages and add them to the database. To manage this we have the `receiver` sub command.

#### Starting new receivers
To start new receivers use the command:
```bash
p2p-layer-deployment simulator receiver start [-d <db-namespace>] [-r <receivers-count>] [-s <db-sink-count>] -n <namespace> 
```
Where the `db-namespace` is the name of the database namespace (see above). `receivers-count` and `db-sink-count` are the number of receivers and db sinks and `namespace` is the name of a deployed namespace.

#### Killing all the receivers
To stop any running receivers run:
```bash
p2p-layer-deployment simulator receiver stop -n <namespace> 
```
#### Viewing all the receivers
To view all the running receivers run:
```bash
p2p-layer-deployment simulator receiver status -n <namespace> 
```

### Managing the simulator senders
The simulator needs to simulate something to send new messages and add keep in the database. To manage this we have the `sender` sub command.

#### Send one off batch of messages
To send one off batch of messages use the `send` command. i.e.
```bash
p2p-layer-deployment simulator sender send [-d <db-namespace>] -n <source-namespace> -p <target-namespace>
```
Where the `db-namespace` is the name of the database namespace (see above). `source-namespace` is the deployed namespace to simulate message sending from and `target-namespace` is the deployed namespace to simulate message sending to.
To see the full set of options see:
```bash
p2p-layer-deployment simulator sender send --help
```
#### Send messages continuously
To send messages continuously use the `start` command. i.e.
```bash
p2p-layer-deployment simulator sender start [-d <db-namespace>] -n <source-namespace> -p <target-namespace>
```
Where the `db-namespace` is the name of the database namespace (see above). `source-namespace` is the deployed namespace to simulate message sending from and `target-namespace` is the deployed namespace to simulate message sending to.
To see the full set of options see:
```bash
p2p-layer-deployment simulator sender start --help
```

#### Killing all the senders
To stop any running sender run:
```bash
p2p-layer-deployment simulator sender stop -n <namespace> 
```
#### Viewing all the senders
To view all the running senders run:
```bash
p2p-layer-deployment simulator sender status -n <namespace> 
```

# After you finish
1. Destroy the cluster.
2. Kill all the running databases.
3. Stop and uninstall Telepresence:
```bash
telepresence quit && telepresence uninstall --everything
```
3. Stop and delete minikube:
```
minikube stop && minikube delete
```

# Using AWS
## Before you begin
1. Install AWS CLI (see [here](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html))
2. Make sure you have access to AWS. (see [here](https://engineering.r3.com/engineering-central/how-we-work/build-logistics-and-tooling/build-and-test/test/eks-cluster-getting-started/#obtaining-sso-access-to-aws))
2.1 After using minikube, make sure to run:
```bash
aws eks update-kubeconfig --name eks-e2e-03
```
To switch back to minikube use:
```bash
kubectl config use-context minikube
```
