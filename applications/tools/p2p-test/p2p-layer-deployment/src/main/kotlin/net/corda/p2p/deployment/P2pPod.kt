package net.corda.p2p.deployment

abstract class P2pPod(kafkaServers: String, tag: String) : Pod() {
    override val pullSecrets = listOf(CordaOsDockerDevSecret.name)
    open val autoStart = true
    abstract val imageName: String
    override val image by lazy {
        "${CordaOsDockerDevSecret.host}/corda-os-$imageName:$tag"
    }
    override val command by lazy {
        if (autoStart) {
            null
        } else {
            listOf("sleep", "infinity")
        }
    }
    override val environmentVariables = mapOf("KAFKA_SERVERS" to kafkaServers)
}
