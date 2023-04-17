This page describes how to build and deploy Corda 5 to a local Kubernetes cluster for the purposes of Corda development.

# Create a Kubernetes cluster

The following instructions assume that you have a single-node Kubernetes cluster running with a Docker daemon.
Two options that meet these requirements and have been tested with these instructions are:

* [Docker Desktop](#install-and-configure-docker-desktop)
* [minikube](#install-minikube)

Docker Desktop provides a simpler user experience but commercial use in larger enterprises requires a paid subscription.
See [Do I need to pay to use Docker Desktop?](https://docs.docker.com/desktop/faqs/general/#do-i-need-to-pay-to-use-docker-desktop) for more details.

## Install and configure Docker Desktop

1. Install Docker Desktop.
   * [macOS](https://docs.docker.com/desktop/mac/install/)
   * [Windows](https://docs.docker.com/desktop/windows/install/)
   * [Linux](https://docs.docker.com/desktop/install/linux-install/)
2. [Enable Kubernetes](https://docs.docker.com/desktop/kubernetes/#enable-kubernetes) in Preferences.
3. Configure your Kubernetes cluster with at least 6 CPU and 8 GB RAM.
   * For macOS, configure the [resources](https://docs.docker.com/desktop/settings/mac/#resources) in the Docker Desktop Preferences.
   * For Linux, configure the [resources](https://docs.docker.com/desktop/settings/linux/#resources) in the Docker Desktop Preferences.
   * For Windows, configure the WSL settings in the [.wslconfig](https://docs.microsoft.com/en-us/windows/wsl/wsl-config#configuration-setting-for-wslconfig) file.

## Install minikube

1. Install [minikube](https://minikube.sigs.k8s.io/docs/start/).
2. Start minikube with at least 8 GB memory and 6 CPUs:

    ```sh
    minikube start --memory 8000 --cpus 6
    ```

3. If you don't already have the `kubectl` CLI installed, set up the following alias:

    ```sh
    alias kubectl="minikube kubectl --"
    ```
# Setup for Helm installation

## Install Helm

1. [Install the Helm CLI](https://helm.sh/docs/intro/install/).
2. Activate CLI completion using the appropriate command for your shell.

    Bash:
    ```bash
    # helm completion --help
    source <(helm completion bash)
    ```

    Zsh:
    ```zsh
    # helm completion --help
    source <(helm completion zsh)
    ```


## Create a Kubernetes namespace

1. If you have multiple Kubernetes clusters, ensure that you are targeting the `kubectl` context for the correct cluster.
    You can list contexts you have defined with:

    ```sh
    kubectl config get-contexts
    ```

    The current context is marked with an asterisk.
    You can switch context, for example:

    ```sh
    kubectl config use-context docker-desktop
    ```

    If you are using Docker Desktop, you can also switch context via the Kubernetes sub-menu.

2. Create a namespace to contain your Corda deployment.
    For example, to create a namespace called `corda` run the command:

    ```sh
    kubectl create namespace corda
    ```

The commands that follow all explicitly specify the namespace to use. However, you can reduce the length of your commands by switching the Kubernetes context to use the newly created namespace:

```
kubectl config set-context --current --namespace=corda
```

Install the [kubectx and kubens](https://github.com/ahmetb/kubectx) tools for an easy way to switch context and namespace from the command line.

# Install Corda prerequisites

Corda requires PostgreSQL and Kafka instances as prerequisites.
One option to obtain these is via the `corda-dev-prereqs` Helm chart.
**Note:** this Helm chart is not designed for availability or scalability and should only be used for development purposes.
The packaged Helm chart can be installed directly from Docker Hub or in source form from GitHub.

## Install Corda prerequisites from Docker Hub

The `corda-dev-prereqs` Helm chart is available packaged on [Docker Hub](https://hub.docker.com/r/corda/corda-dev-prereqs).

1. Install the Helm chart:

    ```sh
    helm install prereqs -n corda oci://registry-1.docker.io/corda/corda-dev-prereqs --timeout 10m --wait
    ```

    The `--wait` option ensures all of the pods are ready before returning.
    The timeout is set to 10 minutes to allow time to pull the images from Docker Hub.
    The process should take significantly less time than this on subsequent installs.

## Install Corda prerequisites from GitHub

The `corda-dev-prereqs` Helm chart is available in source form in the [corda/corda-dev-prereqs](https://github.com/corda/corda-dev-prereqs) GitHub repository.

1. Clone the GitHub repository:

    ```sh
    git clone https://github.com/corda/corda-dev-prereqs.git
    cd corda-dev-prereqs
    ```

2. Install the Helm chart:

    ```sh
    helm install prereqs -n corda charts/corda-dev-prereqs --timeout 10m --wait
    ```

    The `--wait` option ensures all of the pods are ready before returning.
    The timeout is set to 10 minutes to allow time to pull the images from Docker Hub.
    The process should take significantly less time than this on subsequent installs.

# Build the Corda Docker images

1. Clone the [corda/corda-cli-plugin-host](https://github.com/corda/corda-cli-plugin-host) repository:

    ```sh
    git clone https://github.com/corda/corda-cli-plugin-host.git
    ```

2. Clone the [corda/corda-api](https://github.com/corda/corda-api) repository:

    ```sh
    git clone https://github.com/corda/corda-api.git
    ```

3. Clone the [corda/corda-runtime-os](https://github.com/corda/corda-runtime-os) repository:

    ```sh
    git clone https://github.com/corda/corda-runtime-os.git
    ```

4. If you’re using minikube, configure your shell to use the Docker daemon inside minikube so that built images are available directly to the cluster:

    Bash:

    ```bash
    eval $(minikube docker-env)
    ```

    PowerShell:

    ```pwsh
    minikube docker-env --shell=powershell | Invoke-Expression
    ```

    The next step must be run in the shell where this command is executed.

5. Using Java 11, build all of the Corda Docker images with Gradle in the `corda-runtime-os` repository:

    ```sh
    cd corda-runtime-os
    ./gradlew clean publishOSGiImage -PcompositeBuild=true
    ```

# Install Corda

There is a `values-prereqs.yaml` file at the root of the `corda-runtime-os` repository that overrides the default values in the Corda Helm chart.
These values configure the chart to use the images you just built and specify the location of the Kafka and PostgreSQL instances created by the `corda-dev-prereqs` Helm chart.
They also set the initial admin user password to `admin`.

1. Install the chart as follows by running from the root of the `corda-runtime-os` repository:

   ```sh
   helm install corda -n corda charts/corda --values values-prereqs.yaml --wait
   ```

When the command completes, the RPC endpoint should be ready to access.

## Troubleshooting

If the install times out, it indicates that not all of the worker pods reached ready state. Use the following command to list the pods and their current state:

```
kubectl get pods -n corda
```

If a particular pod is failing to start, run the following command to get more details using the name of the pod from the previous output:

```
kubectl describe pod -n corda corda-rpc-worker-8f9f5565-wkzgq
```

If the pod is continually restarting, it is likely that Kubernetes is killing it because it does not reach a healthy state. Check the pod logs, for example:

```
kubectl logs -n corda corda-rpc-worker-8f9f5565-wkzgq
```
For more information about these commands, see [View worker logs](#view-worker-logs).

# View worker logs

To follow the logs for a specific worker pod:

```
kubectl logs -f -n corda corda-rpc-worker-69f9dbcc97-ndllq
```

To retrieve a list of the pods:

```
kubectl get pods -n corda
```

To enable command completion and allow tab-completion of the pod name:

```
kubectl completion -h
```

You can also view the logs for all pods for a deployment. This has the advantage that the name does not change from one release to the next. For example:

```
kubectl logs -f -n corda deploy/corda-rpc-worker
```

To get a list of all deployments:

```
kubectl get deployments -n corda
```

To follow the logs for all pods in the release, use labels:

```
kubectl logs -f -n corda -l app.kubernetes.io/instance=corda --prefix=true
```

For more power (and color), install [stern](https://github.com/wercker/stern).

If you are using minikube, you can use the following command to display the Kubernetes dashboard and then navigate to the logs via **Namespaces** > **Pods** > **Pod logs**:

```
minikube dashboard --url
```

# Access the Corda cluster and run the E2E Tests

1. To access the RPC endpoint, forward the port to `localhost:8888` by running one of these commands:

   Bash:
   ```
   kubectl port-forward -n corda deploy/corda-rpc-worker 8888 &
   ```

   PowerShell:
   ```
   Start-Job -ScriptBlock {kubectl port-forward -n corda deploy/corda-rpc-worker 8888}
   ```
2. Retrieve the password for the initial `admin` user as follows:

   Bash:
   ```
   kubectl get secret corda-initial-admin-user -n corda \
     -o go-template='{{ .data.password | base64decode }}'
   ```

   PowerShell:
    ```
    kubectl get secret corda-initial-admin-user -n corda `
       -o go-template='{{ .data.password | base64decode }}'
    ```

3. From the root directory of the `corda/corda-runtime-os` repository, run this Gradle task to execute the E2E tests:

   ```
   ./gradlew :applications:workers:release:rpc-worker:e2eTest
   ```

# Deploying Corda as per the E2E Test CI

The deployment of Corda in CI for the E2E tests uses multiple Kafka users.
If you need to replicate this behaviour:

1. Deploy the prerequisites with the overrides in `.ci/e2eTests/prereqs.yaml` in the `corda-runtime-os` repository.

2. Run the following bash commands to copy the comma-separated list of generated passwords into separate fields in a new secret:

    ```bash
    KAFKA_PASSWORDS=$(kubectl get secret prereqs-kafka-jaas -n "${NAMESPACE}" -o go-template="{{ index .data \\\"client-passwords\\\" | base64decode }}")
    IFS=',' read -r -a KAFKA_PASSWORDS_ARRAY <<< "$KAFKA_PASSWORDS"
    kubectl create secret generic kafka-credentials -n "${NAMESPACE}" \
      --from-literal=bootstrap="${KAFKA_PASSWORDS_ARRAY[0]}" \
      --from-literal=crypto="${KAFKA_PASSWORDS_ARRAY[1]}" \
      --from-literal=db="${KAFKA_PASSWORDS_ARRAY[2]}" \
      --from-literal=flow="${KAFKA_PASSWORDS_ARRAY[3]}" \
      --from-literal=membership="${KAFKA_PASSWORDS_ARRAY[4]}" \
      --from-literal=p2pGateway="${KAFKA_PASSWORDS_ARRAY[5]}" \
      --from-literal=p2pLinkManager="${KAFKA_PASSWORDS_ARRAY[6]}" \
      --from-literal=rpc="${KAFKA_PASSWORDS_ARRAY[7]}"
    ```

3. Deploy Corda with the overrides specified in `.ci\e2eTests\corda.yaml` in the `corda-runtime-os` repository.

# Redeploying a single work image

To make a change to a single worker image, you can redeploy the worker without recreating the entire installation. For example, to rebuild the RPC worker image:

1. Run this command:

   ```
   ./gradlew :applications:workers:release:rpc-worker:publishOSGiImage -PcompositeBuild=true
   ```

2. List the pods (as described in [View worker logs](#view-worker-logs) and then use the name of the current RPC worker pod to kill it. For example:

   ```
   kubectl delete pod -n corda corda-rpc-worker-69f9dbcc97-ndllq
   ```

When Kubernetes restarts the pod, it picks up the newly built Docker image.

# Attach a remote Java debugging session to a worker

This example shows how to connect the IntelliJ debugger to the `corda-rpc-worker` pod.

By default, debug is not enabled for any of the pods. You must also configure Corda to only create a single replica of the worker to guarantee that work is handled by the pod you are attached to. 

1. There is a `debug.yaml` file in the root of the `corda-runtime-os` repository. Uncomment the lines to enable debugging for the worker you are interested in. For example:

   ```
   workers:
     rpc:
       replicaCount: 1
       debug:
         enabled: true
   ```

2. (Re)install the Helm chart specifying both the `values-prereqs.yaml` and `debug.yaml` as follows:

   Bash:

   ```
   helm upgrade --install corda -n corda \
     charts/corda \
     --values values-prereqs.yaml \
     --values debug.yaml \
     --wait
   ```

   PowerShell:

   ```
   helm upgrade --install corda -n corda `
     charts/corda `
     --values values-prereqs.yaml `
     --values debug.yaml `
     --wait
   ```

3. Expose port 5005 from the pod to localhost:

   Bash:

   ```
   kubectl port-forward -n corda deploy/corda-rpc-worker 5005 &
   ```

   PowerShell:

   ```
   Start-Job -ScriptBlock {kubectl port-forward -n corda deploy/corda-rpc-worker 5005}
   ```

   This command uses the name of the deployment as, unlike the pod name, it stays the same between one Helm release and the next. It does, however, just pick one pod in the deployment at random and attach the debugger to that. That is not an issues in this example as we have configured the number of replicas as 1.

4. To connect IntelliJ to the debug port:

   a. Click **Run** > **Edit Configurations**.

      The *Run/Debug configurations* window is displayed.

   b. Click the plus (+) symbol and select *Remote JVM Debug*.

   c. Enter a **Name** and **Port Number**. 

   d. Click *OK*.

**Note:** To permit debugging without restarting the process, startup, liveness, and readiness probes are disabled when debug is enabled.

IntelliJ users may also be interested in the [Cloud Code plugin](https://github.com/GoogleCloudPlatform/cloud-code-intellij), which enables you to interact with Kubernetes without leaving your IDE.

# Enable Quasar's @Suspendable verification in the Flow Worker

Use the `debug.yaml` file in the root of the `corda-runtime-os` repository when installing the Helm chart:

Bash:

```
helm upgrade --install corda -n corda \
  charts/corda \
  --values values-prereqs.yaml \
  --values debug.yaml \
  --wait
```

PowerShell:

```
helm upgrade --install corda -n corda `
  charts/corda `
  --values values-prereqs.yaml `
  --values debug.yaml `
  --wait
```

**Note:** The verification impacts performance and can be turned off, while still using the content of `debug.yaml` by setting the `flow.verifyInstrumentation` property to `false` or removing it entirely.

# Connect to the cluster DB

To connect to the cluster DB from tooling on your local environment, do the following:

1. Port forward the PostgreSQL pod. For example:

   Bash:

   ```
   kubectl port-forward -n corda svc/prereqs-postgres 5434:5432 &
   ```

    PowerShell:

   ```
   Start-Job -ScriptBlock {kubectl port-forward -n corda svc/prereqs-postgres 5434:5432}
   ```

2. Fetch the superuser’s password from the Kubernetes secret:

   Bash:
   ```
   kubectl get secret prereqs-postgres -n corda \
     -o go-template='{{ index .data "postgres-password" | base64decode }}'
   ```

   PowerShell:
   ```
   kubectl get secret prereqs-postgres -n corda `
     -o go-template='{{ index .data \"postgres-password\" | base64decode }}'
   ```

3. Connect to the DB using your preferred database administration tool with the following properties:
   * Host — `localhost`
   * Port — `5434`
   * Database — `cordacluster`
   * User — `postgres`
   * Password — as determined above

If using Telepresence, you do not require the port forwarding; simply connect using the hostname `prereqs-postgres.corda`. 

# Connect to Kafka (e.g. to run a worker outside of the Kubernetes cluster)

This example connects a Kafka client from outside the cluster, to Kafka running under Kubernetes. 

1. Retrieve the password for the `admin` user:
   ```
   export KAFKA_PASSWORD=$(kubectl get secret -n {{ .Release.Namespace }} {{ include "corda-dev.kafkaName" . }} -o go-template='{{ `{{` }} index .data "admin-password" | base64decode {{ `}}` }}')
   ```

2. Generate the Kafka client properties file:
   ```
   echo "security.protocol=SASL_PLAINTEXT" > client.properties
   echo "sasl.mechanism=PLAIN" >> client.properties
   echo "sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username=\"admin\" password=\"$KAFKA_PASSWORD\";" >> client.properties
   ```

3. Forward the Kafka port:
   ```
   kubectl port-forward -n {{ .Release.Namespace }} $(kubectl get pods -n {{ .Release.Namespace }} --selector=app.kubernetes.io/component=kafka,app.kubernetes.io/instance={{ .Release.Name }} -o=name) 9094 &
   ```

4. Commands can then be run against the the cluster, for example:
   ```
   kafka-topics --list --bootstrap-server localhost:9094 --command-config client.properties
   ```

# Deploy Kafdrop

[Kafdrop](https://github.com/obsidiandynamics/kafdrop) provides an (insecure) web-UI for browsing the contents of a Kafka cluster. In order to deploy kafdrop you'll need to git clone the Kafdrop repo, change into that directory, and make Corda your default namespace before running the command to deploy the container:

```
git clone https://github.com/obsidiandynamics/kafdrop && cd kafdrop
kubectl config set-context --current --namespace=corda
helm upgrade --install kafdrop chart --set kafka.brokerConnect=prereqs-kafka:9092 --set kafka.properties="$(echo -e "security.protocol=SSL\nssl.truststore.type=PEM" | base64)" --set kafka.truststore="$(kubectl get secret prereqs-kafka -o go-template='{{ index .data "ca.crt" }}')" -n corda
```
Now port forward that container to be able to connect to Kafdrop on localhost, If using telepresence then you'll not need this step.

```
kubectl port-forward -n corda svc/kafdrop 9000:9000 &
```
You should now be able to connect to Kafdrop on http://localhost:9000/.

# Clean up

The quickest route to clean up is to delete the entire Kubernetes namespace:

```
kubectl delete ns corda
```

Alternatively, you can clean up the Helm releases, pre-install jobs, and the persistent volumes created by the pre-requisites as follows:

```
helm delete corda -n corda
helm delete prereqs -n corda
kubectl delete job --all -n corda
kubectl delete pvc --all -n corda
```

Usually the above `delete pvc` command also deletes the persistent volumes, but not always. You can check with:
```
kubectl get pv
```
You may have to delete some volumes explicitly.  Assuming that this is the only K8S cluster you are running, you can delete all persistent volumes with this command. Only run this command if you are sure you want to delete _all_ volumes.
```
kubectl delete pv --all
```

# Other recommended Kubernetes tools

* [Cloud Code plugin](https://github.com/GoogleCloudPlatform/cloud-code-intellij) for Kubernetes in IntelliJ
* [stern](https://github.com/wercker/stern) for following logs in multiple containers
* [kubectx and kubens](https://github.com/ahmetb/kubectx) for switching Kubernetes context and namespace
* [Lens](https://k8slens.dev/) for a shiny UI for interacting with your cluster
* [k9s](https://k9scli.io/) for a shiny CLI for interacting with your cluster