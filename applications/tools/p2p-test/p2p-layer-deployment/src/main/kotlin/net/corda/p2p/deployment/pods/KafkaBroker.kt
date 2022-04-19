package net.corda.p2p.deployment.pods

import java.lang.Integer.min

class KafkaBroker(
    index: Int,
    clusterName: String,
    zookeeperConnectString: String,
    details: InfrastructureDetails,
) : Pod() {
    companion object {
        fun kafkaServers(namespace: String, brokersCount: Int) =
            (1..brokersCount).joinToString(",") { index ->
                "kafka-broker-$index.$namespace:${Port.KafkaExternalBroker.port}"
            }

        fun kafka(
            clusterName: String,
            details: InfrastructureDetails,
        ): Collection<Pod> {
            val zookeepers = ZooKeeper.zookeepers(details.zooKeeperCount)
            val zookeeperConnectString = zookeepers.joinToString(",") {
                "${it.app}:${Port.ZooKeeperClientPort.port}"
            }
            val brokers = (1..details.kafkaBrokerCount).map {
                KafkaBroker(
                    it,
                    clusterName,
                    zookeeperConnectString,
                    details,
                )
            }
            val ui = if (!details.disableKafkaUi) {
                listOf(
                    KafkaUi(
                        clusterName,
                        zookeepers.map { "${it.app}:${Port.ZooKeeperClientPort.port}" }.first(),
                        brokers.map { "${it.app}:${Port.KafkaClientBroker.port}" }.first(),
                    )
                )
            } else {
                emptyList()
            }

            return zookeepers + brokers + ui
        }
    }
    override val app = "kafka-broker-$index"
    override val image = "wurstmeister/kafka:2.13-2.8.1"
    override val labels = mapOf("type" to "kafka-broker")

    override val ports = listOf(
        Port.KafkaExternalBroker,
        Port.KafkaClientBroker,
        Port.KafkaInternalBroker,
    )

    override val environmentVariables = mapOf(
        "KAFKA_CLUSTER_ID" to clusterName,
        "ALLOW_PLAINTEXT_LISTENER" to "yes",
        "KAFKA_ZOOKEEPER_CONNECT" to zookeeperConnectString,
        "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP" to "INTERNAL:PLAINTEXT,CLIENT:PLAINTEXT,EXTERNAL:PLAINTEXT",
        "KAFKA_INTER_BROKER_LISTENER_NAME" to "INTERNAL",
        "KAFKA_AUTO_CREATE_TOPICS_ENABLE" to "false",
        "KAFKA_LISTENERS" to
            "INTERNAL://:${Port.KafkaInternalBroker.port}," +
            "CLIENT://:${Port.KafkaClientBroker.port}," +
            "EXTERNAL://:${Port.KafkaExternalBroker.port}",
        "KAFKA_ADVERTISED_LISTENERS" to
            "INTERNAL://$app:${Port.KafkaInternalBroker.port}," +
            "CLIENT://$app:${Port.KafkaClientBroker.port}," +
            "EXTERNAL://$app.$clusterName:${Port.KafkaExternalBroker.port}",
        "KAFKA_MIN_INSYNC_REPLICAS" to "1",
        "KAFKA_DEFAULT_REPLICATION_FACTOR" to min(3, details.kafkaBrokerCount).toString(),
        "KAFKA_NUM_PARTITIONS" to details.defaultPartitionsCount.toString(),
    )

    override val resourceRequest: ResourceRequest = details.kafkaBrokerResourceRequest

    override val readyLog = ".*started \\(kafka.server.KafkaServer\\).*".toRegex()
}
