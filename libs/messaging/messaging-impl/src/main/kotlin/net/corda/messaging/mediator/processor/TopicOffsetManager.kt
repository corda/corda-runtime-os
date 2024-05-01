package net.corda.messaging.mediator.processor

import java.util.concurrent.ConcurrentHashMap

class TopicOffsetManager(val topic: String) {
    private val partitionOffsetManagers = ConcurrentHashMap<Int, PartitionOffsetManager>()
    private fun getPartitionOffsetManager(partition: Int): PartitionOffsetManager {
        return partitionOffsetManagers.computeIfAbsent(partition) {
            PartitionOffsetManager()
        }
    }

    fun getLowestUncommittedOffset(partition: Int): Long? {
        return getPartitionOffsetManager(partition).getLowestUncommittedOffset()
    }

    fun recordPolledOffset(partition: Int, offset: Long) {
        getPartitionOffsetManager(partition).recordPolledOffset(offset)
    }

    fun recordOffsetPreCommit(partition: Int, offset: Long) {
        getPartitionOffsetManager(partition).recordOffsetPreCommit(offset)
    }

    fun recordOffsetTag(partition: Int, offset: Long, tag: String) {
        getPartitionOffsetManager(partition).recordOffsetTag(offset, tag)
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

    override fun toString(): String {
        return "${this.javaClass.simpleName}(topic=$topic, partitions=${
            partitionOffsetManagers.map { "(${it.key}=${it.value})" }.joinToString(", ")
        })"
    }
}