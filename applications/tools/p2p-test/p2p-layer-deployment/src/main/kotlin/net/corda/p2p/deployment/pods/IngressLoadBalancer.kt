package net.corda.p2p.deployment.pods

class IngressLoadBalancer(
    podSelectorType: String,
    private val port: Port,
) : Yamlable {
    private val headlessService = HeadlessLoadBalancer(
        podSelectorType, listOf(port)
    )
    override fun yamls(namespaceName: String) =
        headlessService.yamls(namespaceName) +
            listOf(
                mapOf(
                    "apiVersion" to "networking.k8s.io/v1",
                    "kind" to "Ingress",
                    "metadata" to mapOf(
                        "name" to "ingress-load-balancer",
                        "namespace" to namespaceName,
                        "labels" to mapOf("app" to "ingress-load-balancer"),
                        "annotations" to mapOf(
                            "cluster-autoscaler.kubernetes.io/safe-to-evict" to "false",
                        )
                    ),
                    "spec" to mapOf(
                        "defaultBackend" to mapOf(
                            "service" to mapOf(
                                "name" to "load-balancer",
                                "port" to mapOf("number" to port.port)
                            )
                        )
                    )
                )
            )
}
