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
            }
            return gateways + balancer
        }
    }
    override val imageName = "p2p-gateway"

    override val readyLog = ".*Waiting for gateway to start.*".toRegex()

    override val otherPorts = if (details.lbType == LbType.K8S) {
        emptyList()
    } else {
        listOf(
            Port.Gateway
        )
    }
}
