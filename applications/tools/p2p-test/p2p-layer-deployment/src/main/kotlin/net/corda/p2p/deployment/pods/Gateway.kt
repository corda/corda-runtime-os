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
                LbType.NGINX -> if (details.nginxCount <= 1) {
                    NginxLoadBalancer(
                        gateways.map { it.app },
                        true,
                    )
                } else {
                    HeadlessNginxLoadBalancer(
                        details.nginxCount,
                        gateways.map { it.app },
                    )
                }

                LbType.HEADLESS -> HeadlessService(
                    "p2p-gateway"
                )
            }
            return gateways + balancer
        }
    }
    override val imageName = "p2p-gateway"

    override val readyLog = ".*Waiting for gateway to start.*".toRegex()

    override val otherPorts = when (details.lbType) {
        // In K8S load balancer the load balancer service will listen to the Gateway port, so no need to create a service.
        LbType.K8S -> emptyList()
        LbType.NGINX -> listOf(
            Port.Gateway
        )
        // In HEADLESS load balancer the headless service will act as the load balancer, no need to create an extra service per gateway.
        LbType.HEADLESS -> emptyList()
    }
}
