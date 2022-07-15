package net.corda.messagebus.db.util

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.v5.base.util.contextLogger
import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility class for keeping track of the current offsets, stored by [CordaTopicPartition].
 */
class WriteOffsets(
    initialOffsets: Map<CordaTopicPartition, Long>
) {
    companion object {
        private val log = contextLogger()
    }

    private val latestOffsets = ConcurrentHashMap(initialOffsets)

    @Synchronized
    fun getNextOffsetFor(topicPartition: CordaTopicPartition): Long {
        return latestOffsets.compute(topicPartition) { _: CordaTopicPartition, offset: Long? ->
            offset?.plus(1) ?: 0L
        }.also {
            log.info("Returning offset ($topicPartition, $it) from $this, ${ManagementFactory.getRuntimeMXBean().name}")
        } ?: throw CordaMessageAPIFatalException("Next offset should never be null.")
    }
}
