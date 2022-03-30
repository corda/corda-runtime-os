package net.corda.p2p.deployment.pods

class HeadlessService(
    private val podSelectorType: String,

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
                    "clusterIP" to "None",
                    "ports" to listOf(
                        mapOf(
                            "port" to Port.Gateway.port,
                            "name" to Port.Gateway.displayName,
                        )
                    ),
                    "selector" to mapOf("type" to podSelectorType)
                )
            )
        )
}
