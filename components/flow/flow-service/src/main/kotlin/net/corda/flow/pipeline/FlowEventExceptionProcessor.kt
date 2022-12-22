package net.corda.flow.pipeline

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.flow.pipeline.exceptions.FlowStrayEventException
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.StateAndEventProcessor

/**
 * The [FlowEventExceptionProcessor] is responsible for handling any exceptions thrown while processing a [FlowEvent]
 */
interface FlowEventExceptionProcessor {

    /**
     * Sets the configuration for the exception processor
     *
     * @param config The [SmartConfig] containing the settings used in the processor
     */
    fun configure(config: SmartConfig)

    /**
     * Processes a [Throwable] and provides the pipeline response.
     *
     * This handling is the fallback if all other error processing fails. As a result, it is essential that no errors
     * are thrown from this function.
     *
     * @param throwable The [Throwable] thrown during processing.
     *
     * @return The updated response.
     */
    fun process(throwable: Throwable): StateAndEventProcessor.Response<Checkpoint>

    /**
     * Processes a [FlowTransientException] and provides the pipeline response.
     *
     * Used to handle event retries.
     *
     * @param exception The [FlowTransientException] thrown during processing
     * @param context The [FlowEventContext] at the point of failure.
     *
     * @return The updated response.
     */
    fun process(exception: FlowTransientException, context: FlowEventContext<*>): StateAndEventProcessor.Response<Checkpoint>

    /**
     * Processes a [FlowFatalException] and provides the pipeline response.
     *
     * Used when a flow has failed due to errors in the pipeline code. User code failures should be handled via normal
     * pipeline processing.
     *
     * @param exception The [FlowFatalException] thrown during processing.
     *
     * @return The updated response.
     */
    fun process(exception: FlowFatalException, context: FlowEventContext<*>): StateAndEventProcessor.Response<Checkpoint>

    /**
     * Processes a [FlowEventException] and provides the pipeline response.
     *
     * Invoked if event processing failed, for example a session event for a failed session.
     *
     * @param exception The [FlowEventException] thrown during processing
     *
     * @return The updated response.
     */
    fun process(exception: FlowEventException, context: FlowEventContext<*>): StateAndEventProcessor.Response<Checkpoint>

    /**
     * Processes a [FlowStrayEventException] and provides the pipeline response.
     *
     * Invoked if an event should be discarded, for example a spurious wakeup.
     *
     * @param exception The [FlowStrayEventException] thrown during processing
     *
     * @return The updated response.
     */
    fun process(exception: FlowStrayEventException, context: FlowEventContext<*>): StateAndEventProcessor.Response<Checkpoint>

    /**
     * Processes a [FlowPlatformException] and provides the pipeline response.
     *
     * Invoked when a platform exception is encountered after the flow has run, which needs to be communicated back to
     * the user code.
     *
     * @param exception The [FlowPlatformException] thrown during processing
     *
     * @return The updated response.
     */
    fun process(exception: FlowPlatformException, context: FlowEventContext<*>): StateAndEventProcessor.Response<Checkpoint>
}
