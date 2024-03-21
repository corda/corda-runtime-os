package net.corda.p2p.linkmanager

import net.corda.messaging.api.records.EventLogRecord

/**
 * Class which wraps a message, so we can do event tracing by passing the original event log record to a lower layer.
 */
internal typealias TraceableItem<T, E> = ItemWithSource<EventLogRecord<String, E>?, T>
internal data class ItemWithSource<SOURCE, ITEM>(
    val item: ITEM,
    val source: SOURCE,
)