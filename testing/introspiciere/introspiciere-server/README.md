# Introspiciere-server

Introspiciere-server is a REST server. It can be easily deployed in any K8s and inject any messages in Kafka.

## Deploy in K8s

### Dependencies

Make sure you have installed:
* Docker
* Kubectl
* Minikube

In mac they can all be easily installed using _brew_.

### Build Introspiciere image

```shell
# cd into introspiciere-server directory and compile the jar
cd testing/introspiciere/introspiciere-server
../../../gradlew jar -x test 

# build the docker image and add it to minikube image repository
minikube start # If you haven't started already
eval $(minikube docker-env)
docker build -t introspiciere-server .
```
### Deploy introspiciere

When having more than one host, ideally you should use a different introspiciere server for each host.

```shell
# create namespace (if necessary)
kubectl create namespace host0
kubectl apply -f k8s-introspiciere-server.yaml -n host0

# Forward load balancer port to localhost (from another terminal)
# TODO: Expose the service in a better way
kubectl port-forward service/introspiciere-server 7070:7070 -n host0

# Test the service
curl localhost:7070/helloworld
# > Hello world!!
```

### Delete introspiciere deployment

```shell
kubectl delete -f k8s-introspiciere-server.yaml -n host0 
kubectl delete namespace host0
```

## Run as a local process

You may as well just want to run the server as a local process.

```shell
cd testing/introspiciere/introspiciere-server
../../../gradlew jar -x test

KAFKA_BROKERS=`minikube ip`:`kubectl get service my-cluster-kafka-external-bootstrap -o=jsonpath='{.spec.ports[0].nodePort}' -n kafka` \
  java -jar build/libs/introspiciere-server-5.0.0.0-SNAPSHOT.jar 
```

# Old instructions that we don't want to lose but have changed

```shell
# cd into introspiciere-server directory
cd testing/introspiciere/introspiciere-server

# compile
../../../gradlew jar -x test 

# init minikube. It only needs to be done once
minikube start
minikube ssh
sudo ip link set docker0 promisc on
exit
eval $(minikube docker-env)
kubectl apply -f - << END
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: corda-sc
provisioner: k8s.io/minikube-hostpath
reclaimPolicy: Delete
volumeBindingMode: Immediate
END

# build image and add it to minikube image repository
docker build -t introspiciere-server .

# create namespace (if necessary)
kubectl create namespace host-0

# deploy introspiciere-server
kubectl apply -f k8s-introspiciere-server.yaml -n host-0

# Forward load balancer port to localhost (from another terminal)
kubectl port-forward service/introspiciere-server 7070:7070 -n host-0

# Test the service
curl localhost:7070/helloworld
# > Hello world!!

# delete introspiciere-server deployment and namespace
kubectl delete -f k8s-introspiciere-server.yaml -n host-0 
kubectl delete namespace host-0
```