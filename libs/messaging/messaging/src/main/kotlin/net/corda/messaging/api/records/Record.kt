package net.corda.messaging.api.records

/**
 * Object to encapsulate the events stored on topics
 * @property topic Defines the id of the topic the [Record] is stored on when sending on the message bus.
 * This field is not required for records being sent via synchronous RPC.
 * @property key Is the unique per topic key for a [Record].
 * @property value The value of the [Record].
 * @property timestamp The timestamp of when the message was produced.
 * @property headers Optional list of headers to added to the message.
 */
data class Record<K : Any, V : Any>(
    val topic: String?,
    val key: K,
    val value: V?,
    val timestamp: Long = 0,
    val headers: List<Pair<String, String>> = listOf()
)
