This directory contains scripts to simplify deploying the app simulator.
Prerequisites for running the scripts: kubectl connected to a K8s cluster and a K8s secret, docker-registry-cred, containing the docker registry credentials.
It is also assumed that corda-cli-plugin-host is checked out and has the same parent-directory as this repository (this can be overridden in setting.sh).

### Multi-cluster mode [Default]
deploy.sh deploys 3 worker clusters (a, b and mgm) by default into K8s.
To override the default behaviour specify a K8s namespace for each worker cluster on the command line.
e.g.
```bash 
deploy.sh my-cluster-mgm my-cluster-a
```
will deploy 2 worker clusters into the K8's namespaces `my-cluster-mgm` and `my-cluster-a`.

### Single-cluster mode
To run the app simulator in single cluster mode, set the following environment variable:
```shell
export CLUSTER_MODE=SINGLE_CLUSTER
```
In this mode, deploy.sh deploys 1 worker cluster (a) into K8s.

onBoardCluster.sh sets up a dynamic network with MGM and two members. This requires that the worker cluster(s) have been deployed.

runReceiver.sh deploys receiving and dbsink app simulators into cluster-b.  
runSender.sh deploys sending app simulator into cluster-a.  
In single-cluster mode, all app simulators are deployed onto cluster-a.

settings.sh contains some settings which can be updated to change the deployment.
sender.yaml and receiver.yaml contain some settings for the receiving, and dbsink app simulators and the sending app simulator, respectively.

tearDown.sh tears down the worker cluster and kills the port forwarding processes.
