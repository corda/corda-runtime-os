package net.corda.p2p.deployment

class Simulator(
    kafkaServers: String,
    tag: String,
    index: Int,
) : P2pPod(kafkaServers, tag, index) {
    override val imageName = "app-simulator"
    override val autoStart = false
}
