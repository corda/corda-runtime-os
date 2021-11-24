package net.corda.p2p.deployment

class ConfiguratorPod(tag: String, kafkaServers: String) : P2pPod(kafkaServers, tag) {
    override val app = "configurator"
    override val imageName = "configuration-publisher"
    override val autoStart = false
}
