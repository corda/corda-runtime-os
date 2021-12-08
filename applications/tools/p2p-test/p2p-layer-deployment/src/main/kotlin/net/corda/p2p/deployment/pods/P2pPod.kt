package net.corda.p2p.deployment.pods

import net.corda.p2p.deployment.CordaOsDockerDevSecret

abstract class P2pPod(
    kafkaServers: String,
    index: Int,
    details: P2PDeploymentDetails,
) : Pod() {
    override val pullSecrets = listOf(CordaOsDockerDevSecret.name)
    open val otherPorts: Collection<Port> = emptyList()
    abstract val imageName: String
    override val image by lazy {
        "${CordaOsDockerDevSecret.host}/corda-os-$imageName:${details.tag}"
    }
    override val app by lazy {
        "$imageName-$index"
    }
    override val labels by lazy {
        mapOf("type" to imageName)
    }
    override val environmentVariables by lazy {
        mapOf(
            "KAFKA_SERVERS" to kafkaServers,
            "INSTANCE_ID" to index.toString(),
        ) + if (details.debug) {
            mapOf("JAVA_TOOL_OPTIONS" to "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:8002")
        } else {
            emptyMap()
        }
    }

    override val resourceRequest = details.resourceRequest

    override val ports by lazy {
        otherPorts + if (details.debug) {
            listOf(
                Port(
                    "debug",
                    8002
                )
            )
        } else {
            emptyList()
        }
    }
}
