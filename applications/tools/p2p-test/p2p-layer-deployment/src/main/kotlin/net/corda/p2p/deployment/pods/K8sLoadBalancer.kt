package net.corda.p2p.deployment.pods

class K8sLoadBalancer(
    private val podSelectorType: String,
    private val ports: Collection<Port>,
) : Yamlable {
    override fun yamls(namespaceName: String) =
        listOf(
            mapOf(
                "apiVersion" to "v1",
                "kind" to "Service",
                "metadata" to mapOf(
                    "name" to "load-balancer",
                    "namespace" to namespaceName,
                    "labels" to mapOf("app" to "load-balancer"),
                    "annotations" to mapOf(
                        "cluster-autoscaler.kubernetes.io/safe-to-evict" to "false",
                    )
                ),
                "spec" to mapOf(
                    "type" to "LoadBalancer",
                    "ports" to ports.map {
                        mapOf(
                            "port" to it.port,
                            "name" to it.displayName,
                        )
                    },
                    "selector" to mapOf("type" to podSelectorType)
                )
            )
        )
}
