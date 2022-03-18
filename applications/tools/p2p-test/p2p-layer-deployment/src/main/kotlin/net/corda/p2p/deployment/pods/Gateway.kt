package net.corda.p2p.deployment.pods

class Gateway(
    index: Int,
    kafkaServers: String,
    details: P2PDeploymentDetails,
) : P2pPod(kafkaServers, index, details) {
    companion object {
        fun gateways(
            kafkaServers: String,
            details: P2PDeploymentDetails,
        ): Collection<Yamlable> {
            val gateways = (1..details.gatewayCount).map {
                Gateway(it, kafkaServers, details)
            }
            val balancer = when (details.lbType) {
                LbType.K8S -> K8sLoadBalancer(
                    "p2p-gateway",
                    listOf(Port.Gateway),
                )
                LbType.NGINX -> NginxLoadBalancer(
                    gateways.map { it.app },
                )
                LbType.HEADLESS, LbType.MEMBERSHIP_HEADLESS -> HeadlessLoadBalancer(
                    "p2p-gateway",
                    listOf(Port.Gateway),
                )
                LbType.INGRESS -> IngressLoadBalancer(
                    "p2p-gateway",
                    Port.Gateway,
                )
                LbType.NO_LB, LbType.MEMBERSHIP, LbType.NO_SERVICE -> EmptyYamlable()
                else -> TestingOneLoadBalance(
                    details.lbType,
                    gateways.map { it.app },
                )
            }
            return gateways + balancer
        }
    }
    override val imageName = "p2p-gateway"

    override val readyLog = ".*Waiting for gateway to start.*".toRegex()

    override val otherPorts = when (details.lbType) {
        LbType.K8S, LbType.HEADLESS, LbType.INGRESS, LbType.MEMBERSHIP_HEADLESS, LbType.NO_SERVICE ->
            // In K8S load balancer the load balancer service will listen to the Gateway port, so no need to create a service.
            emptyList()
        else -> listOf(
            Port.Gateway
        )
    }
}
