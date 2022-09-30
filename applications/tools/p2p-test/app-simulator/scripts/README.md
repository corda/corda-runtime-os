This directory contains scripts to simplify deploying the app simulator.

deploy.sh deploys 3 worker clusters (a, b and mgm) into K8s. After which it sets up a dynamic network.

runReceiver.sh deploys receiving and dbsink app simulators into cluster b.
runSender.sh deploys sending app simulator into cluster a.

settings.sh contains some settings which can be updated to change the deployment.
sender.yaml and receiver.yaml contain some settings for the receiving, and dbsink app simulators and the sending app simulator, respectively.

tearDown.sh tears down the worker cluster and kills the port forwarding processes.
