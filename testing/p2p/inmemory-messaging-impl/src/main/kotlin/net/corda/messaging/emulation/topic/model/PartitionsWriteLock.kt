package net.corda.messaging.emulation.topic.model

internal class PartitionsWriteLock(
    partitions: Collection<Partition>
) {
    private val locks = partitions.distinct()
        .sortedWith(
            compareBy(
                { it.topicName },
                { it.partitionId }
            )
        ).map {
            it.lock.writeLock()
        }

    fun write(block: () -> Unit) {
        if (locks.isEmpty()) {
            block()
        } else {
            locks.forEach {
                it.lock()
            }

            try {
                block()
            } finally {
                locks.reversed().forEach {
                    it.unlock()
                }
            }
        }
    }
}
