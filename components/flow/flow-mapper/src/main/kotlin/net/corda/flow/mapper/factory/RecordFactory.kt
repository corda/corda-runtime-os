package net.corda.flow.mapper.factory

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.SessionEvent
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record
import java.time.Instant

/**
 * Create [Record]
 * Topic for [Record] is returned based on:
 * - The message direction
 * - whether the counterparty is on the same cluster (local)
 * @return A record for p2p.out or local
 */
interface RecordFactory {

    /**
     * Forward [Record] of [SessionEvent] using:
     * @return A record of SessionEvent
     */
    @Suppress("LongParameterList")
    fun forwardEvent(
        sessionEvent: SessionEvent,
        instant: Instant,
        flowConfig: SmartConfig,
        flowId: String,
        isInteropSession: Boolean
    ): Record<*, *>

    /**
     * Forward [Record] of [SessionError]
     * @return A record of SessionError
     */
    @Suppress("LongParameterList")
    fun forwardError(
        sessionEvent: SessionEvent,
        exceptionEnvelope: ExceptionEnvelope,
        instant: Instant,
        flowConfig: SmartConfig,
        flowId: String,
        isInteropSession: Boolean
    ): Record<*, *>
}