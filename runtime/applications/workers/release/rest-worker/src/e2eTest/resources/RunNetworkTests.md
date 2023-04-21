# Run Network End-to-End (E2E) tests
Before running the tests for the membership groups, you will need to configure the endpoints for REST and P2P for all running clusters. The e2e tests require either a single cluster for single cluster tests (cluster A) or three clusters for multi-cluster tests (clusters A, B, and C). You can either rely on the defaults (which will only work for a locally deployed cluster and not for multi-cluster testing), set these as build properties, or set these as system properties.

Below is an example of properties which can be set in the projects `build.properties`
``` properties
e2eClusterARestHost = corda-rest-worker.ccrean-cluster-a
e2eClusterARestPort = 443
e2eClusterBRestHost = corda-rest-worker.ccrean-cluster-b
e2eClusterBRestPort = 443
e2eClusterCRestHost = corda-rest-worker.ccrean-cluster-mgm
e2eClusterCRestPort = 443

e2eClusterAP2pHost = corda-p2p-gateway-worker.ccrean-cluster-a
e2eClusterAP2pPort = 8080
e2eClusterBP2pHost = corda-p2p-gateway-worker.ccrean-cluster-b
e2eClusterBP2pPort = 8080
e2eClusterCP2pHost = corda-p2p-gateway-worker.ccrean-cluster-mgm
e2eClusterCP2pPort = 8080
```

Or if you wish to set these as system properties you can set them as 
```bash
export E2E_CLUSTER_A_REST_HOST=corda-rest-worker.ccrean-cluster-a
export E2E_CLUSTER_A_REST_PORT=443
export E2E_CLUSTER_B_REST_HOST=corda-rest-worker.ccrean-cluster-b
export E2E_CLUSTER_B_REST_PORT=443
export E2E_CLUSTER_C_REST_HOST=corda-rest-worker.ccrean-cluster-mgm
export E2E_CLUSTER_C_REST_PORT=443

export E2E_CLUSTER_A_P2P_HOST=corda-p2p-gateway-worker.ccrean-cluster-a
export E2E_CLUSTER_A_P2P_PORT=8080
export E2E_CLUSTER_B_P2P_HOST=corda-p2p-gateway-worker.ccrean-cluster-b
export E2E_CLUSTER_B_P2P_PORT=8080
export E2E_CLUSTER_C_P2P_HOST=corda-p2p-gateway-worker.ccrean-cluster-mgm
export E2E_CLUSTER_C_P2P_PORT=8080
```

Note: In the above examples, the host values represent ${worker}.${k8s-namespace} where worker is the corda worker and k8s-namespace is the kubernetes namespace the worker is running in. `kubefwd` is used in this case to forward ports for three corda clusters running on AWS. These values will vary depending on how you are deploying corda cluster.

When running multi-cluster tests, you will need to have three corda clusters running. The best solution may be to deploy the three clusters on AWS. An example script for doing this can be found here:
* [Create multi-cluster deployment](create.multi.clusters.sh) 
