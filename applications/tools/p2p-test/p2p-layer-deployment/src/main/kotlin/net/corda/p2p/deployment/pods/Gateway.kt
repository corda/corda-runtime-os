package net.corda.p2p.deployment.pods

class Gateway(
    index: Int,
    kafkaServers: String,
    override val hosts: Collection<String>,
    tag: String,
    debug: Boolean,
    override val autoStart: Boolean,
) : P2pPod(kafkaServers, tag, index, debug) {
    companion object {
        fun gateways(
            count: Int,
            hostNames: Collection<String>,
            kafkaServers: String,
            tag: String,
            debug: Boolean,
            avoidStart: Boolean,
        ): Collection<Pod> {
            val gateways = (1..count).map {
                Gateway(it, kafkaServers, hostNames, tag, debug, !avoidStart)
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
}
