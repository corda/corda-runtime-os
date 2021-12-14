package net.corda.p2p.deployment.pods

import java.lang.Integer.min

class KafkaBroker(
    index: Int,
    clusterName: String,
    zookeeperConnectString: String,
    replicationFactor: Int,
    override val resourceRequest: ResourceRequest?,
) : Pod() {
    companion object {
        fun kafkaServers(namespace: String, brokersCount: Int) =
            (1..brokersCount).joinToString(",") { index ->
                "kafka-broker-$index.$namespace:${Port.KafkaExternalBroker.port}"
            }

        fun kafka(
            clusterName: String,
            zookeepersCount: Int,
            brokersCount: Int,
            kafkaUi: Boolean,
            resourceRequest: ResourceRequest?
        ): Collection<Pod> {
            val zookeepers = ZooKeeper.zookeepers(zookeepersCount)
            val zookeeperConnectString = zookeepers.map {
                "${it.app}:${Port.ZooKeeperClientPort.port}"
            }.joinToString(",")
            val brokers = (1..brokersCount).map {
                KafkaBroker(
                    it,
                    clusterName,
                    zookeeperConnectString,
                    min(3, brokersCount),
                    resourceRequest,
                )
            }
            val ui = if (kafkaUi) {
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
    override val image = "wurstmeister/kafka:latest"
    override val labels = mapOf("type" to "kafka-broker")

    override val ports = listOf(
        Port.KafkaExternalBroker,
        Port.KafkaClientBroker,
        Port.KafkaInternalBroker,
    )

    override val environmentVariables = mapOf(
        "KAFKA_BROKER_ID" to index.toString(),
        "KAFKA_CLUSTER_ID" to clusterName,
        "ALLOW_PLAINTEXT_LISTENER" to "yes",
        "KAFKA_ZOOKEEPER_CONNECT" to zookeeperConnectString,
        "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP" to "INTERNAL:PLAINTEXT,CLIENT:PLAINTEXT,EXTERNAL:PLAINTEXT",
        "KAFKA_INTER_BROKER_LISTENER_NAME" to "INTERNAL",
        "KAFKA_LISTENERS" to
            "INTERNAL://:${Port.KafkaInternalBroker.port}," +
            "CLIENT://:${Port.KafkaClientBroker.port}," +
            "EXTERNAL://:${Port.KafkaExternalBroker.port}",
        "KAFKA_ADVERTISED_LISTENERS" to
            "INTERNAL://$app:${Port.KafkaInternalBroker.port}," +
            "CLIENT://$app:${Port.KafkaClientBroker.port}," +
            "EXTERNAL://$app.$clusterName:${Port.KafkaExternalBroker.port}",
        "KAFKA_MIN_INSYNC_REPLICAS" to "1",
        "KAFKA_DEFAULT_REPLICATION_FACTOR" to replicationFactor.toString(),
        "KAFKA_NUM_PARTITIONS" to "5",
    )

    override val readyLog = ".*started \\(kafka.server.KafkaServer\\).*".toRegex()
}
