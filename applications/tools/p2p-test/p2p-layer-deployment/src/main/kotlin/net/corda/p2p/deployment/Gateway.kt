package net.corda.p2p.deployment

class Gateway(
    index: Int,
    kafkaServers: String,
    override val hosts: Collection<String>,
    tag: String,
) : P2pPod(kafkaServers, tag, index) {
    companion object {
        fun gateways(
            count: Int,
            hostNames: Collection<String>,
            kafkaServers: String,
            tag: String,
        ): Collection<Pod> {
            val gateways = (1..count).map {
                Gateway(it, kafkaServers, hostNames, tag)
            }
            val balancer = LoadBalancer(
                hostNames,
                gateways.map { it.app }
            )
            return gateways + balancer
        }
    }
    override val imageName = "p2p-gateway"
    override val ports = listOf(
        Port("p2p-gateway", 80)
    )
}
