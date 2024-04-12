package net.corda.messaging.mediator.processor

class TopicOffsetManager {
    private val partitionOffsetManagers = mutableMapOf<Int, PartitionOffsetManager>()
    private fun getPartitionOffsetManager(partition: Int): PartitionOffsetManager {
        return partitionOffsetManagers.computeIfAbsent(partition) {
            PartitionOffsetManager()
        }
    }

    fun recordPolledOffset(partition: Int, offset: Long) {
        getPartitionOffsetManager(partition).recordPolledOffset(offset)
    }

    fun recordOffsetPreCommit(partition: Int, offset: Long) {
        getPartitionOffsetManager(partition).recordOffsetPreCommit(offset)
    }

    fun getCommittableOffsets(): Map<Int, Long> {
        return partitionOffsetManagers.filterValues {
            it.getCommittableOffset() != null
        }.mapValues { it.value.getCommittableOffset()!! }
    }

    fun commit() {
        partitionOffsetManagers.forEach {
            it.value.commit()
        }
    }

    fun rollback() {
        partitionOffsetManagers.forEach {
            it.value.rollback()
        }
    }

    fun assigned() {
        partitionOffsetManagers.clear()
    }

    fun revokePartition(partition: Int) {
        partitionOffsetManagers.remove(partition)
    }

    fun assignPartition(partition: Int) {
        getPartitionOffsetManager(partition).assigned()
    }
}