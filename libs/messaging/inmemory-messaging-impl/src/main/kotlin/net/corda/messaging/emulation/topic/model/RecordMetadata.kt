package net.corda.messaging.emulation.topic.model

import net.corda.messaging.api.records.Record

/**
 * [record] with its [offset] and [partition] in the topic.
 */
data class RecordMetadata(val offset: Long, val record: Record<*, *>, val partition: Int) {
    fun <K : Any, V : Any> castToType(keyClass: Class<K>, valueClass: Class<V>): Record<K, V>? {
        @Suppress("UNCHECKED_CAST")
        return if (
            keyClass.isInstance(record.key) &&
            (record.value == null || valueClass.isInstance(record.value))
        ) {
            record as Record<K, V>
        } else {
            null
        }
    }
}
