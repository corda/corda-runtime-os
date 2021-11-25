package net.corda.p2p.deployment.pods

import net.corda.p2p.deployment.CordaOsDockerDevSecret

abstract class P2pPod(kafkaServers: String, tag: String, index: Int) : Pod() {
    override val pullSecrets = listOf(CordaOsDockerDevSecret.name)
    open val autoStart = true
    abstract val imageName: String
    override val image by lazy {
        "${CordaOsDockerDevSecret.host}/corda-os-$imageName:$tag"
    }
    override val app by lazy {
        "$imageName-$index"
    }
    override val command by lazy {
        if (autoStart) {
            null
        } else {
            listOf("sleep", "infinity")
        }
    }
    override val environmentVariables = mapOf(
        "KAFKA_SERVERS" to kafkaServers,
        "INSTANCE_ID" to index.toString(),
    )
}
