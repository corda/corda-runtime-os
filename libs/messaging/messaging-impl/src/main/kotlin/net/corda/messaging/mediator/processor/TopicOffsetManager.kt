package net.corda.messaging.mediator.processor

class TopicOffsetManager {
    private val partitionOffsetManagers = mutableMapOf<Int, PartitionOffsetManager>()
    fun getPartitionOffsetManager(partition: Int): PartitionOffsetManager {
        return partitionOffsetManagers.computeIfAbsent(partition) {
            PartitionOffsetManager()
        }
    }

    fun assigned() {
        partitionOffsetManagers.clear()
    }
}