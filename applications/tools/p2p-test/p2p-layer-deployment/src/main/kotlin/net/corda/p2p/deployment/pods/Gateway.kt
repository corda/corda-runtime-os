package net.corda.p2p.deployment.pods

class Gateway(
    index: Int,
    kafkaServers: String,
    override val hosts: Collection<String>,
    tag: String,
    debug: Boolean,
) : P2pPod(kafkaServers, tag, index, debug) {
    companion object {
        fun gateways(
            count: Int,
            hostNames: Collection<String>,
            kafkaServers: String,
            tag: String,
            debug: Boolean,
        ): Collection<Pod> {
            val gateways = (1..count).map {
                Gateway(it, kafkaServers, hostNames, tag, debug)
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
