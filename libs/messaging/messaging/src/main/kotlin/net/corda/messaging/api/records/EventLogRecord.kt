package net.corda.messaging.api.records

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
    val timestamp: Long = 0,
    val headers: List<Pair<String, String>> = listOf()
)
