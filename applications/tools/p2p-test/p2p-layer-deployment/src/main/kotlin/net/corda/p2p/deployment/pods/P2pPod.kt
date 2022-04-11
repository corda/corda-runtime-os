package net.corda.p2p.deployment.pods

import net.corda.p2p.deployment.DockerSecrets

abstract class P2pPod(
    kafkaServers: String,
    details: P2PDeploymentDetails,
    count: Int,
) : Pod() {
    open val otherPorts: Collection<Port> = emptyList()
    abstract val imageName: String
    override val statefulSetReplicas = count
    override val image by lazy {
        "${DockerSecrets.cordaHost}/corda-os-$imageName:${details.tag}"
    }
    override val app by lazy {
        imageName
    }
    override val labels by lazy {
        mapOf("type" to imageName)
    }
    override val environmentVariables by lazy {
        mapOf(
            "KAFKA_SERVERS" to kafkaServers,
            "INSTANCE_ID_FROM_HOSTNAME_REGEX" to "$imageName-(\\d+)",
        ) + if (details.debug) {
            mapOf("JAVA_TOOL_OPTIONS" to "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${Port.Debug.port}")
        } else {
            emptyMap()
        }
    }

    override val resourceRequest = details.resourceRequest

    override val ports by lazy {
        otherPorts + if (details.debug) {
            listOf(
                Port.Debug
            )
        } else {
            emptyList()
        }
    }
}
