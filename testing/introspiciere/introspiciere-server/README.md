# Introspiciere-server

## Dependencies

Make sure you have installed:
* Docker
* Kubectl
* Minikube

In mac they can all be easily installed using _brew_.

## Instructions to build Introspiciere image

```shell
# cd into introspiciere-server directory
cd testing/introspiciere/introspiciere-server

# compile
../../../gradlew jar -x test 

# build image and add it to minikube image repository
minikube start                  # if not done yet
eval $(minikube docker-env)     # if not done yet
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