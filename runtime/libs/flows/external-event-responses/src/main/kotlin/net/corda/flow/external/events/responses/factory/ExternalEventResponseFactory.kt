package net.corda.flow.external.events.responses.factory

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.messaging.api.records.Record

/**
 * [ExternalEventResponseFactory] creates [Record]s containing [ExternalEventResponse]s to be sent back to the calling
 * flow after processing an external event.
 */
interface ExternalEventResponseFactory {

    /**
     * Creates a response representing the successful processing of an external event.
     *
     * @param requestId The [ExternalEventContext.requestId] that the received event contained.
     * @param flowId The [ExternalEventContext.flowId] that the received event contained.
     * @param payload The Avro schema object to send.
     *
     * @return A [FlowEvent] record containing a [ExternalEventResponse] to send back to the calling flow.
     */
    fun success(requestId: String, flowId: String, payload: Any): Record<String, FlowEvent>

    /**
     * Creates a response representing the successful processing of an external event.
     *
     * @param flowExternalEventContext The [ExternalEventContext] that the received event contained.
     * @param payload The Avro schema object to send.
     *
     * @return A [FlowEvent] record containing a [ExternalEventResponse] to send back to the calling flow.
     */
    fun success(flowExternalEventContext: ExternalEventContext, payload: Any): Record<String, FlowEvent>

    /**
     * Creates a response representing a failed attempt at processing an external event which should be retried by
     * the calling flow.
     *
     * @param flowExternalEventContext The [ExternalEventContext] that the received event contained.
     * @param throwable The error that occurred.
     *
     * @return A [FlowEvent] record containing a [ExternalEventResponse] to send back to the calling flow.
     */
    fun transientError(flowExternalEventContext: ExternalEventContext, throwable: Throwable): Record<String, FlowEvent>

    /**
     * Creates a response representing a failed attempt at processing an external event which should be retried by
     * the calling flow.
     *
     * @param flowExternalEventContext The [ExternalEventContext] that the received event contained.
     * @param exceptionEnvelope The error that occurred.
     *
     * @return A [FlowEvent] record containing a [ExternalEventResponse] to send back to the calling flow.
     */
    fun transientError(
        flowExternalEventContext: ExternalEventContext,
        exceptionEnvelope: ExceptionEnvelope
    ): Record<String, FlowEvent>

    /**
     * Creates a response representing a failed attempt at processing an external event which should be propagated to
     * the calling flow and rethrown.
     *
     * @param flowExternalEventContext The [ExternalEventContext] that the received event contained.
     * @param throwable The error that occurred.
     *
     * @return A [FlowEvent] record containing a [ExternalEventResponse] to send back to the calling flow.
     */
    fun platformError(flowExternalEventContext: ExternalEventContext, throwable: Throwable): Record<String, FlowEvent>

    /**
     * Creates a response representing a failed attempt at processing an external event which should be propagated to
     * the calling flow and rethrown.
     *
     * @param flowExternalEventContext The [ExternalEventContext] that the received event contained.
     * @param exceptionEnvelope The error that occurred.
     *
     * @return A [FlowEvent] record containing a [ExternalEventResponse] to send back to the calling flow.
     */
    fun platformError(
        flowExternalEventContext: ExternalEventContext,
        exceptionEnvelope: ExceptionEnvelope
    ): Record<String, FlowEvent>

    /**
     * Creates a response representing a failed attempt at processing an external event which is fatal and should end
     * the sending flow.
     *
     * @param flowExternalEventContext The [ExternalEventContext] that the received event contained.
     * @param throwable The error that occurred.
     *
     * @return A [FlowEvent] record containing a [ExternalEventResponse] to send back to the calling flow.
     */
    fun fatalError(flowExternalEventContext: ExternalEventContext, throwable: Throwable): Record<String, FlowEvent>

    /**
     * Creates a response representing a failed attempt at processing an external event which is fatal and should end
     * the sending flow.
     *
     * @param flowExternalEventContext The [ExternalEventContext] that the received event contained.
     * @param exceptionEnvelope The error that occurred.
     *
     * @return A [FlowEvent] record containing a [ExternalEventResponse] to send back to the calling flow.
     */
    fun fatalError(
        flowExternalEventContext: ExternalEventContext,
        exceptionEnvelope: ExceptionEnvelope
    ): Record<String, FlowEvent>
}
