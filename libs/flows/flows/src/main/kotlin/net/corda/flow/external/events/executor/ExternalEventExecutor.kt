package net.corda.flow.external.events.executor

import net.corda.flow.external.events.handler.ExternalEventHandler
import net.corda.v5.base.annotations.Suspendable

/**
 * [ExternalEventExecutor] sends events to processors external to the flow pipeline and receives responses from them.
 */
interface ExternalEventExecutor {

    /**
     * Sends an event to an external processor and awaits its response.
     *
     * [execute] resumes with either a:
     * - Response of type [RESUME].
     * - An exception.
     *
     * @param requestId The unique request id of the event.
     * @param handlerClass The [ExternalEventHandler] that is called to create the event to send and convert the
     * received response into an acceptable object to resume with.
     * @param parameters The [PARAMETERS] object.
     *
     * @param PARAMETERS The type to pass to the handler just after suspending/creating the event.
     * @param RESPONSE The type that is received as a response from the external processor.
     * @param RESUME The type that the flow will resume with after calling [execute].
     */
    @Suspendable
    fun <PARAMETERS : Any, RESPONSE, RESUME> execute(
        requestId: String,
        handlerClass: Class<out ExternalEventHandler<PARAMETERS, RESPONSE, RESUME>>,
        parameters: PARAMETERS
    ): RESUME

    /**
     * Sends an event to an external processor and awaits its response.
     *
     * [execute] resumes with either a:
     * - Response of type [RESUME].
     * - An exception.
     *
     * @param handlerClass The [ExternalEventHandler] that is called to create the event to send and convert the
     * received response into an acceptable object to resume with.
     * @param parameters The [PARAMETERS] object.
     *
     * @param PARAMETERS The type to pass to the handler when suspending/creating the event.
     * @param RESPONSE The type that is received as a response from the external processor.
     * @param RESUME The type that the flow will resume with after calling [execute].
     */
    @Suspendable
    fun <PARAMETERS : Any, RESPONSE, RESUME> execute(
        handlerClass: Class<out ExternalEventHandler<PARAMETERS, RESPONSE, RESUME>>,
        parameters: PARAMETERS
    ): RESUME
}