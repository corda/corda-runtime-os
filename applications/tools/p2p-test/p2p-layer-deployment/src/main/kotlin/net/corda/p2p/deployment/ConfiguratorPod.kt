package net.corda.p2p.deployment

class ConfiguratorPod(kafkaServers: String) : Pod() {
    override val app = "configurator"
    // YIFT: Replace with actual image
    //override val image = "corda-os-docker-dev.software.r3.com/corda-os-configuration-publisher:latest"
    override val image = "azul/zulu-openjdk-alpine:11"
    override val command = listOf("sleep", "infinity")
    override val environmentVariables = mapOf("KAFKA_SERVERS" to kafkaServers)
}
