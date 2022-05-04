package net.corda.messagebus.db.util

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility class for keeping track of the current offsets, stored by [CordaTopicPartition].
 */
class WriteOffsets(
    dbAccess: DBAccess
) {
    private val latestOffsets = ConcurrentHashMap(dbAccess.getMaxOffsetsPerTopicPartition())

    @Synchronized
    fun getNextOffsetFor(topicPartition: CordaTopicPartition): Long {
        return latestOffsets.compute(topicPartition) { _: CordaTopicPartition, offset: Long? ->
            offset?.plus(1) ?: 0L
        } ?: throw CordaMessageAPIFatalException("Next offset should never be null.")
    }
}
