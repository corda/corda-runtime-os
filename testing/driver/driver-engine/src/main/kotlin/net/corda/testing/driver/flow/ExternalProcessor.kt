package net.corda.testing.driver.flow

import net.corda.data.flow.event.FlowEvent
import net.corda.messaging.api.records.Record

fun interface ExternalProcessor {
    fun processEvent(record: Record<*, *>): Record<String, FlowEvent>
}
