package net.corda.p2p.deployment

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
                    "server.$indexToString=$server:2888:3888;2181"
                }
                ZooKeeper(index, serverString)
            }
        }
    }
    override val app = "zookeeper-$index"
    override val image = "zookeeper"

    override val ports = listOf(
        Port("client-port", 2181),
        Port("follower-port", 2888),
        Port("election-port", 3888),
    )
    override val environmentVariables = mapOf(
        "ZOO_MY_ID" to index.toString(),
        "ZOO_SERVERS" to serverString
    )
}
