package net.corda.introspiciere.domain

data class TopicDefinition(
    val name: String,
    val partitions: Int = DEFAULT_PARTITIONS,
    val replicationFactor: Short = DEFAULT_REPLICATION_FACTOR
) {
    companion object {
        const val DEFAULT_PARTITIONS: Int = 1
        const val DEFAULT_REPLICATION_FACTOR: Short = 1
    }
}