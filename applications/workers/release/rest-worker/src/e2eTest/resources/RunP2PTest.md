# Run P2P End-to-End (E2E) tests
Before running the tests for the P2P we need to add an environment variables to indicate to the tests how to access 
the Kafka deployment. To do that one can either install a new deployment (for example, by following the instruction in [here](https://github.com/corda/corda-runtime-os/wiki/Local-development-with-Kubernetes))or by connecting to a running cluster.
The environment variables should start with `CORDA_KAFKA_` and have suffix that is the Kafka property name in upper case with `_` instead of `.`. For example, the `bootstrap.servers` should be `CORDA_KAFKA_BOOTSTRAP_SERVERS`.
To overwrite the hosts files, set the `JDK_HOSTS_FILE` environment variable.
To overwrite the security client, set the `JAVA_SECURITY_AUTH_LOGIN_CONFIG` environment variable.

A few example scripts:
* [To install a local minikube cluster](install.minikube.sh).
* [To install a remote AWS cluster](install.aws.sh) (Replace the `telepresence` with `kubefwd` if `telepresence` isn't working).
* [To connect to a e2e running cluster](connect.e2e.sh) (Replace the `telepresence` with `kubefwd` if `telepresence` isn't working).
