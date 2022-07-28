package net.corda.messaging.api.records

import io.opentelemetry.context.Context

/**
 * Similar to a [Record] but represents a record already stored in a (partitioned) event log,
 * thus also contains the (partition, offset) pair of the record.
 */
data class EventLogRecord<K : Any, V : Any>(
    val topic: String,
    val key: K,
    val value: V?,
    val partition: Int,
    val offset: Long,
    val context: Context? = null
)