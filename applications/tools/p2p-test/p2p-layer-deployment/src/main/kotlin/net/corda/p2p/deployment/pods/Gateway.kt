package net.corda.p2p.deployment.pods

class Gateway(
    index: Int,
    kafkaServers: String,
    override val hosts: Collection<String>,
    details: P2PDeploymentDetails,
) : P2pPod(kafkaServers, index, details) {
    companion object {
        fun gateways(
            hostNames: Collection<String>,
            kafkaServers: String,
            details: P2PDeploymentDetails,
        ): Collection<Pod> {
            val gateways = (1..details.gatewayCount).map {
                Gateway(it, kafkaServers, hostNames, details)
            }
            val balancer = LoadBalancer(
                hostNames,
                gateways.map { it.app }
            )
            return gateways + balancer
        }
    }
    override val imageName = "p2p-gateway"
    override val otherPorts = listOf(
        Port("p2p-gateway", 1433)
    )

    override val readyLog = ".*Waiting for gateway to start.*".toRegex()
}
