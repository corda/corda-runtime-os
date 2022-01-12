package net.corda.p2p.deployment.pods

enum class Port(val displayName: String, val port: Int) {
    Gateway("p2p-gateway", 1433),
    KafkaClientBroker("client-broker", 9092),
    KafkaExternalBroker("external-broker", 9093),
    KafkaInternalBroker("internal-broker", 9091),
    Http("http", 80),
    Debug("debug", 8002),
    Psql("psql", 5432),
    ZooKeeperClientPort("client-port", 2181),
    ZooKeeperFollowerPort("follower-port", 2888),
    ZooKeeperElectionPort("election-port", 3888),
}
