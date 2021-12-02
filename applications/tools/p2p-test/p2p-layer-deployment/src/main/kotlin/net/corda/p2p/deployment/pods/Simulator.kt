package net.corda.p2p.deployment.pods

class Simulator(
    kafkaServers: String,
    tag: String,
    index: Int,
    debug: Boolean,
) : P2pPod(kafkaServers, tag, index, debug) {
    override val imageName = "app-simulator"
    override val autoStart = false
}
