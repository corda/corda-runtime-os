package net.corda.flow.external.events.executor

import net.corda.flow.external.events.factory.ExternalEventFactory
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
     * @param factoryClass The [ExternalEventFactory] that is called to create the event to send and convert the
     * received response into an acceptable object to resume with.
     * @param parameters The [PARAMETERS] object. This object must be @CordaSerializable.
     *
     * @param PARAMETERS The type to pass to the factory just after suspending/creating the event.
     * @param RESPONSE The type received as a response from the external processor.
     * @param RESUME The type the flow resumes with after calling [execute].
     * @return The object that the flow will resume with.
     */
    @Suspendable
    fun <PARAMETERS : Any, RESPONSE : Any, RESUME> execute(
        factoryClass: Class<out ExternalEventFactory<PARAMETERS, RESPONSE, RESUME>>,
        parameters: PARAMETERS
    ): RESUME
}