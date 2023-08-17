package net.corda.flow.mapper.factory

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record
import java.time.Instant

interface RecordFactory {
    /**
     * Create [Record] after checking if the cluster is local.
     * @return A record for p2p.out or local
     */

    fun forwardEvent(
        sessionEvent: SessionEvent,
        instant: Instant,
        flowConfig: SmartConfig,
        messageDirection: MessageDirection
        ): Record<*, *>

    fun forwardError(
        sessionEvent: SessionEvent,
        exceptionEnvelope: ExceptionEnvelope,
        instant: Instant,
        flowConfig: SmartConfig,
        messageDirection: MessageDirection
    ): Record<*, *>

    fun getSessionEventOutputTopic(
        sessionEvent: SessionEvent,
        messageDirection: MessageDirection
    ): String
}