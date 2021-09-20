package net.corda.messaging.emulation.topic.model

enum class PartitionStrategy {
    DIVIDE_PARTITIONS,
    SHARE_PARTITIONS,
    MANUAL,
}
