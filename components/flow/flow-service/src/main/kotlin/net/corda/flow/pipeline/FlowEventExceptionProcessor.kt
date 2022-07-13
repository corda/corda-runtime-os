package net.corda.flow.pipeline

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.flow.pipeline.exceptions.FlowTransientException
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.StateAndEventProcessor
import java.util.concurrent.CancellationException

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
     * Processes an [Exception] and updates the [FlowEventContext]
     *
     * @param exception The [Exception] throw during processing
     *
     * @return the updated [FlowEventContext]
     * @throws [CancellationException] if the event should be moved to the DLQ
     */
    fun process(exception: Exception): StateAndEventProcessor.Response<Checkpoint>

    /**
     * Processes an [FlowTransientException] and updates the [FlowEventContext]
     *
     * @param exception The [Exception] throw during processing
     *
     * @return the updated [FlowEventContext]
     * @throws [CancellationException] if the event should be moved to the DLQ
     */
    fun process(exception: FlowTransientException): StateAndEventProcessor.Response<Checkpoint>

    /**
     * Processes an [FlowFatalException] and updates the [FlowEventContext]
     *
     * @param exception The [Exception] throw during processing
     *
     * @return the updated [FlowEventContext]
     * @throws [CancellationException] if the event should be moved to the DLQ
     */
    fun process(exception: FlowFatalException): StateAndEventProcessor.Response<Checkpoint>

    /**
     * Processes an [FlowEventException] and updates the [FlowEventContext]
     *
     * @param exception The [Exception] throw during processing
     *
     * @return the updated [FlowEventContext]
     * @throws [CancellationException] if the event should be moved to the DLQ
     */
    fun process(exception: FlowEventException): StateAndEventProcessor.Response<Checkpoint>

    fun process(exception: FlowPlatformException): StateAndEventProcessor.Response<Checkpoint>
}
