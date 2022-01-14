package net.corda.p2p.deployment.pods

class ZooKeeper(
    index: Int,
    serverString: String,
) : Pod() {
    companion object {
        fun zookeepers(count: Int): Collection<Pod> {
            return (1..count).map { index ->
                val serverString = (1..count).joinToString(" ") { indexToString ->
                    val server = if (index == indexToString) {
                        "0.0.0.0"
                    } else {
                        "zookeeper-$indexToString"
                    }
                    "server.$indexToString=" +
                        "$server:${Port.ZooKeeperFollowerPort.port}" +
                        ":${Port.ZooKeeperElectionPort.port};" +
                        "${Port.ZooKeeperClientPort.port}"
                }
                ZooKeeper(index, serverString)
            }
        }
    }
    override val app = "zookeeper-$index"
    override val image = "zookeeper"

    override val ports = listOf(
        Port.ZooKeeperClientPort,
        Port.ZooKeeperElectionPort,
        Port.ZooKeeperFollowerPort,
    )
    override val environmentVariables = mapOf(
        "ZOO_MY_ID" to index.toString(),
        "ZOO_SERVERS" to serverString
    )
}
