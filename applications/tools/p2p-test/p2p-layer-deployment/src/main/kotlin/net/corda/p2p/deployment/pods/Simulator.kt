package net.corda.p2p.deployment.pods

import net.corda.p2p.deployment.CordaOsDockerDevSecret

class Simulator(
    kafkaServers: String,
    tag: String,
    simulatorConfig: String,
) : Job() {
    override val pullSecrets = listOf(CordaOsDockerDevSecret.name)
    override val image by lazy {
        "${CordaOsDockerDevSecret.host}/corda-os-app-simulator:$tag"
    }
    override val rawData = listOf(
        TextRawData(
            "simulator-config",
            "/opt/config",
            listOf(TextFile("config.conf", simulatorConfig))
        )
    )
    override val command = listOf(
        "java",
        "-jar",
        "/opt/override/app-simulator.jar",
        "--simulator-config",
        "/opt/config/config.conf"
    )
    override val environmentVariables by lazy {
        mapOf(
            "KAFKA_SERVERS" to kafkaServers,
            "INSTANCE_ID" to simulatorConfig.hashCode().toString(),
        )
    }

    override val app by lazy {
        "simulator-${simulatorConfig.hashCode()}-${System.nanoTime()}"
    }
}
