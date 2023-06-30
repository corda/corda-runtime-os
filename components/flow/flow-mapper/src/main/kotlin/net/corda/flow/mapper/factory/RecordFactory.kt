package net.corda.flow.mapper.factory

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record
import java.time.Instant

interface RecordFactory {
    /**
     * Create [Record] after checking if the cluster is local.
     * @return A record for p2p.out or local
     */

    fun createAndSendRecord(
        eventKey: String,
        sessionEvent: SessionEvent,
        flowMapperState: FlowMapperState?,
        instant: Instant = Instant.now(),
        flowConfig: SmartConfig,
        messageDirection: MessageDirection,
        exceptionEnvelope: ExceptionEnvelope
    ): Record<*, *>
}