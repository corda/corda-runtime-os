package net.corda.flow.external.events.handler

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.state.FlowCheckpoint

/**
 * The [ExternalEventHandler] interface is used by [ExternalEventExecutor] to create events to send to external
 * processors and receive responses from them.
 *
 * __Important__, [ExternalEventHandler] was partially created to ensure that Avro objects are not serialized in flow
 * fibers.
 * - [suspending] should return an [ExternalEventRecord] that contains an Avro serializable payload to send to a
 * processor. Do not pass an Avro object in via [PARAMETERS].
 * - [resuming] receives an Avro object and should return a non-Avro object that can be serialized.
 *
 * @param PARAMETERS The type that is received by the handler just after suspending/creating the event.
 * @param RESPONSE The type that is received as a response from the external processor.
 * @param RESUME The type that the flow will resume with after being called by [ExternalEventExecutor]. [RESUME]
 * __cannot be an Avro object__.
 */
interface ExternalEventHandler<PARAMETERS : Any, RESPONSE, RESUME> {

    /**
     * [suspending] is called by [ExternalEventExecutor] when the after the calling flow suspends.
     *
     * Creates an [ExternalEventRecord] representing the event to send to an external processor.
     *
     * @param checkpoint The [FlowCheckpoint] which can be modified if required.
     * @param flowExternalEventContext The [ExternalEventContext] that should be embedded into the event sent to the
     * external processor.
     * @param parameters The [PARAMETERS] passed to the handler after suspending.
     *
     * @return A [ExternalEventRecord] representing the event to send to an external processor.
     */
    fun suspending(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: PARAMETERS
    ): ExternalEventRecord

    /**
     * [resuming] is called after receiving a response from an external processor and before the calling flow resumes.
     *
     * Returns a [RESUME] to resume the flow with.
     *
     * @param checkpoint The [FlowCheckpoint] which can be modified if required.
     * @param response The [RESPONSE] that was received from an external processor.
     *
     * @return A [RESUME] to resume the flow with.
     */
    fun resuming(checkpoint: FlowCheckpoint, response: RESPONSE): RESUME
}