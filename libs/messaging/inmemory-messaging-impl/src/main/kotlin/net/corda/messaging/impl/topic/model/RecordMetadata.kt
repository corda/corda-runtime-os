package net.corda.messaging.impl.topic.model

import net.corda.messaging.api.records.Record

/**
 * [record] with its [offset] in the topic.
 */
class RecordMetadata(val offset: Long, val record: Record<*, *>)
