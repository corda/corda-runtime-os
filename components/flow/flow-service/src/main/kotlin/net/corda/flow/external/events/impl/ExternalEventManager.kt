package net.corda.flow.external.events.impl

import java.time.Instant
import net.corda.data.flow.event.external.ExternalEvent
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.state.external.ExternalEventState
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record

/**
 * [ExternalEventManager] encapsulates external event behaviour by creating and modifying [ExternalEventState]s.
 */
interface ExternalEventManager {

    /**
     * Processes an event to send, creating a new [ExternalEventState] with an [ExternalEvent] to send.
     *
     * @param flowId The flow id of the calling flow.
     * @param requestId The request id of the external event.
     * @param factoryClassName The name of the [ExternalEventFactory] that will be used to resume the flow with.
     * @param eventRecord The [ExternalEventRecord] created by a [ExternalEventFactory] when suspending the calling flow.
     * @param instant The current time.
     *
     * @return A new [ExternalEventState] containing an [ExternalEvent] to send.
     */
    fun processEventToSend(
        flowId: String,
        requestId: String,
        factoryClassName: String,
        eventRecord: ExternalEventRecord,
        instant: Instant
    ): ExternalEventState

    /**
     * Processes a received response.
     *
     * @param externalEventState The [ExternalEventState] of the flow receiving the response.
     * @param externalEventResponse The received [ExternalEventResponse].
     *
     * @return An updated [ExternalEventState].
     */
    fun processResponse(
        externalEventState: ExternalEventState,
        externalEventResponse: ExternalEventResponse
    ): ExternalEventState

    /**
     * Has the [ExternalEventState] received an [ExternalEventResponse].
     *
     * @param externalEventState The [ExternalEventState] to check.
     *
     * @return `true` if a response has been received, `false` otherwise.
     */
    fun hasReceivedResponse(externalEventState: ExternalEventState): Boolean

    /**
     * Gets the received [ExternalEventResponse] from the [ExternalEventState].
     *
     * Should be paired with [hasReceivedResponse].
     *
     * @param externalEventState The [ExternalEventState] to get the response from.
     * @param responseType The expected type of the response, used for deserializing the response.
     *
     * @return The received response.
     */
    fun getReceivedResponse(externalEventState: ExternalEventState, responseType: Class<*>): Any

    /**
     * Gets the event to send from an [ExternalEventState].
     *
     * @param externalEventState The [ExternalEventState] to get the event from.
     * @param instant The current time.
     * @param config The [SmartConfig] to use.
     *
     * @return A [Pair] containing an updated [ExternalEventState] and a nullable [Record] representing the event to
     * send to external processors. If the event does not need to be sent/resent, then `null` will be returned.
     */
    fun getEventToSend(
        externalEventState: ExternalEventState,
        instant: Instant,
        config: SmartConfig
    ): Pair<ExternalEventState, Record<*, *>?>
}