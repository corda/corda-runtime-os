package net.corda.flow.external.events.factory

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.state.FlowCheckpoint

/**
 * The [ExternalEventFactory] interface is used by [ExternalEventExecutor] to create events to send to external
 * processors and receive responses from them.
 *
 * __Important__, [ExternalEventFactory] was partially created to ensure that Avro objects are not serialized in flow
 * fibers.
 * - [createExternalEvent] should return an [ExternalEventRecord] that contains an Avro serializable payload to send to
 * a processor. Do not pass an Avro object in via [PARAMETERS].
 * - [resumeWith] receives an Avro object and should return a non-Avro object that can be serialized.
 *
 * @param PARAMETERS The factory receives this type after suspending/creating the event.
 * @param RESPONSE The type received as a response from the external processor.
 * @param RESUME The type the flow will resume with after being called by [ExternalEventExecutor]. [RESUME]
 * __cannot be an Avro object__.
 */
interface ExternalEventFactory<PARAMETERS : Any, RESPONSE: Any, RESUME> {

    /**
     * The [RESPONSE] type that the factory receives from the external processor.
     */
    val responseType: Class<RESPONSE>

    /**
     * [createExternalEvent] is called to create an [ExternalEventRecord] by [ExternalEventExecutor] after the calling
     * flow suspends.
     *
     * External processors receive the [ExternalEventRecord.payload] contained in [ExternalEventRecord].
     *
     * Exceptions must not be thrown from this method, otherwise the flow will fail without a chance to handle the
     * error. The data passed into this method should be verified beforehand, so that once passed in, it is guaranteed
     * to succeed.
     *
     * @param checkpoint The [FlowCheckpoint] which can be modified if required.
     * @param flowExternalEventContext The [ExternalEventContext] that should be embedded into the event sent to the
     * external processor.
     * @param parameters The [PARAMETERS] passed to the factory after suspending.
     *
     * @return A [ExternalEventRecord] representing the event to send to an external processor.
     */
    fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: PARAMETERS
    ): ExternalEventRecord

    /**
     * [resumeWith] is called to create a [RESUME] to resume the flow with.
     *
     * This is called after receiving a response from an external processor and before the calling flow resumes.
     *
     * Exceptions must not be thrown from this method, otherwise the flow will fail without a chance to handle the
     * error. Instead, validate the response in the flow after it has resumed and throw an exception if needed.
     *
     * @param checkpoint The [FlowCheckpoint] which can be modified if required.
     * @param response The [RESPONSE] received from an external processor.
     *
     * @return A [RESUME] to resume the flow with.
     */
    fun resumeWith(checkpoint: FlowCheckpoint, response: RESPONSE): RESUME
}