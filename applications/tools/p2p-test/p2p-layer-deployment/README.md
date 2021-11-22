To build run:
```bash
./gradlew :applications:tools:p2p-test:p2p-layer-deployment:install
```

To run:
```bash
./applications/tools/p2p-test/p2p-layer-deployment/build/install/p2p-layer-deployment/bin/p2p-layer-deployment
```

To access the database directly:
```bash
kubectl exec -it -n p2p-layer $(kubectl get pod -n p2p-layer | grep postgres| awk '{print $1}') -- bash
```
Or
```
kubectl exec -it -n p2p-layer $(kubectl get pod -n p2p-layer | grep postgres| awk '{print $1}') -- psql -U corda
```

Delete the cluster:
```bash
kubectl delete namespace p2p-layer
```
Create the cluster
```bash
./applications/tools/p2p-test/p2p-layer-deployment/build/install/p2p-layer-deployment/bin/p2p-layer-deployment | kubectl apply -f -
```