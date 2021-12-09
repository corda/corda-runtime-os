package net.corda.p2p.deployment.pods

class KafkaUi(
    clusterName: String,
    zooKeeper: String,
    broker: String
) : Pod() {
    override val app = "kafka-ui"
    override val image = "provectuslabs/kafka-ui"
    override val ports = listOf(Port.Http)
    override val environmentVariables =
        mapOf(
            "KAFKA_CLUSTERS_0_NAME" to clusterName,
            "KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS" to broker,
            "KAFKA_CLUSTERS_0_ZOOKEEPER" to zooKeeper,
            "KAFKA_CLUSTERS_0_READONLY" to "false",
            "SERVER_PORT" to Port.Http.port.toString(),
        )
}
