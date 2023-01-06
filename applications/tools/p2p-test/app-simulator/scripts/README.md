This directory contains scripts to simplify deploying the app simulator.
Prerequisites for running the scripts: kubectl connected to a K8s cluster and a K8s secret, docker-registry-cred, containing the docker registry credentials.
Also it is assumed that corda-cli-plugin-host is checked and has the same parent-directory as this repository (this can be overridden in setting.sh).

deploy.sh deploys 3 worker clusters (a, b and mgm) into K8s. After which it sets up a dynamic network.

runReceiver.sh deploys receiving and dbsink app simulators into cluster b.
runSender.sh deploys sending app simulator into cluster a.

settings.sh contains some settings which can be updated to change the deployment.
sender.yaml and receiver.yaml contain some settings for the receiving, and dbsink app simulators and the sending app simulator, respectively.

tearDown.sh tears down the worker cluster and kills the port forwarding processes.
