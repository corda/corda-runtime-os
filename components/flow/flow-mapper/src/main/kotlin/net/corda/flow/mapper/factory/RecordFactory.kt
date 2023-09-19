package net.corda.flow.mapper.factory

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.SessionEvent
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record
import java.time.Instant

/**
 * Factory for constructing records for flow session events, based on a source event received by the mapper.
 *
 * When the mapper receives a flow session event, it must decide what to do with it. Instances of [RecordFactory] are
 * responsible for constructing a record to be correctly forwarded once it has been established that forwarding the
 * session event is the correct thing to do.
 */
interface RecordFactory {

    /**
     * Forward a session event to the correct place, based on the source session event.
     *
     * Inbound events are sent to the local flow engine. Outbound events are forwarded to the relevant counterparty.
     *
     * @param sourceEvent The source session event to be forwarded
     * @param instant A timestamp of when this event was received in the mapper
     * @param flowConfig The current flow processor configuration
     * @param flowId The flow ID of the mapper state held for this event (if applicable). Required for inbound events.
     * @return A [Record] with the correct topic, key, and payload for the required destination.
     */
    fun forwardEvent(
        sourceEvent: SessionEvent,
        instant: Instant,
        flowConfig: SmartConfig,
        flowId: String
        ): Record<*, *>

    /**
     * Forward a session error to the correct place, based on the source session event.
     *
     * Inbound events trigger an error to be sent to the local flow engine. Outbound events trigger an error to be sent
     * to the relevant counterparty.
     *
     * This method should be used to pass errors onwards, or to turn an event into an error without changing the
     * direction it is currently travelling.
     *
     * @param sourceEvent The source session event to be forwarded
     * @param exceptionEnvelope The error to forward onwards.
     * @param instant A timestamp of when this event was received in the mapper
     * @param flowConfig The current flow processor configuration
     * @param flowId The flow ID of the mapper state held for this event (if applicable). Required for inbound events.
     * @return A [Record] with the correct topic, key, and payload for the required destination.
     */
    fun forwardError(
        sourceEvent: SessionEvent,
        exceptionEnvelope: ExceptionEnvelope,
        instant: Instant,
        flowConfig: SmartConfig,
        flowId: String
    ): Record<*, *>

    /**
     * Create an error record to be sent back to the party that created the source event.
     *
     * Inbound events are sent back to the counterparty that originally sent the event. Outbound events are not
     * currently handled as the flow ID is unlikely to be available in this case.
     *
     * This method should be used to short circuit passing a session error to the local flow engine. Usually this will
     * happen if the error is for a flow that does not exist on the local flow engine's side.
     *
     * @param sourceEvent The source event that triggered the error
     * @param exceptionEnvelope The error to send back
     * @param instant A timestamp of when this event was received in the mapper
     * @param flowConfig The current flow processor configuration
     * @return A [Record] with the correct topic, key, and payload for the required destination.
     */
    fun sendBackError(
        sourceEvent: SessionEvent,
        exceptionEnvelope: ExceptionEnvelope,
        instant: Instant,
        flowConfig: SmartConfig
    ): Record<*, *>
}