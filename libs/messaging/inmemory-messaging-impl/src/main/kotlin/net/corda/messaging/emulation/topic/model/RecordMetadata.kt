package net.corda.messaging.emulation.topic.model

import net.corda.messaging.api.records.Record

/**
 * [record] with its [offset] in the topic.
 */
data class RecordMetadata(val offset: Long, val record: Record<*, *>, val partition: Int)
