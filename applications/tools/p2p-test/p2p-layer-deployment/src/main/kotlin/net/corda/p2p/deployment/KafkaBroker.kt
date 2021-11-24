package net.corda.p2p.deployment

class KafkaBroker(
    index: Int,
    clusterName: String,
    zookeeperConnectString: String
) : Pod() {
    companion object {
        fun kafkaServers(namespace: String, brokersCount: Int) =
            (1..brokersCount).map { index ->
                "kafka-broker-$index.$namespace:9093"
            }.joinToString(",")
        fun kafka(
            clusterName: String,
            zookeepersCount: Int,
            brokersCount: Int
        ): Collection<Pod> {
            val zookeepers = ZooKeeper.zookeepers(zookeepersCount)
            val zookeeperConnectString = zookeepers.map {
                "${it.app}:2181"
            }.joinToString(",")
            val brokers = (1..brokersCount).map {
                KafkaBroker(it, clusterName, zookeeperConnectString)
            }

            return zookeepers + brokers
        }
    }
    override val app = "kafka-broker-$index"
    override val image = "wurstmeister/kafka:latest"

    override val ports = listOf(
        Port("client-broker", 9092),
        Port("external-broker", 9093),
        Port("internal-broker", 9091),
    )

    override val environmentVariables = mapOf(
        "KAFKA_BROKER_ID" to index.toString(),
        "KAFKA_CLUSTER_ID" to clusterName,
        "ALLOW_PLAINTEXT_LISTENER" to "yes",
        "KAFKA_ZOOKEEPER_CONNECT" to zookeeperConnectString,
        "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP" to "INTERNAL:PLAINTEXT,CLIENT:PLAINTEXT,EXTERNAL:PLAINTEXT",
        "KAFKA_INTER_BROKER_LISTENER_NAME" to "INTERNAL",
        "KAFKA_LISTENERS" to "INTERNAL://:9091,CLIENT://:9092,EXTERNAL://:9093",
        "KAFKA_ADVERTISED_LISTENERS" to "INTERNAL://$app:9091,CLIENT://$app:9092,EXTERNAL://$app.$clusterName:9093",
        "KAFKA_MIN_INSYNC_REPLICAS" to "1",
        "KAFKA_DEFAULT_REPLICATION_FACTOR" to "3",
        "KAFKA_NUM_PARTITIONS" to "5",
    )
}
