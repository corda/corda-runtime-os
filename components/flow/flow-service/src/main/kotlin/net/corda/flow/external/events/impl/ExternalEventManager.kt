package net.corda.flow.external.events.impl

import java.time.Instant
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.state.external.ExternalEventState
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record

interface ExternalEventManager {

    fun processEventToSend(
        flowId: String,
        requestId: String,
        factoryClassName: String,
        eventRecord: ExternalEventRecord,
        instant: Instant
    ): ExternalEventState

    fun processEventReceived(
        externalEventState: ExternalEventState,
        externalEventResponse: ExternalEventResponse
    ): ExternalEventState

    fun getReceivedResponse(externalEventState: ExternalEventState): Any?

    fun getEventToSend(
        flowId: String,
        externalEventState: ExternalEventState,
        instant: Instant,
        config: SmartConfig
    ): Pair<ExternalEventState, Record<*, *>?>
}