package net.corda.p2p.linkmanager

import net.corda.messaging.api.records.EventLogRecord

typealias EndPoint = String

/**
 * Class which wraps a message, so we can do event tracing by passing the original event log record to a lower layer.
 */
internal data class TraceableItem<T, E: Any>(
    val item: T,
    val originalRecord: EventLogRecord<String, E>?
)
