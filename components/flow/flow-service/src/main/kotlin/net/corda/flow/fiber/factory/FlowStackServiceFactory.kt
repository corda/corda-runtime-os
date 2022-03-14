package net.corda.flow.fiber.factory

import net.corda.data.flow.state.Checkpoint
import net.corda.flow.fiber.FlowStackService

/**
 * The [FlowStackServiceFactory] is responsible for creating an instance of the [FlowStackService] for
 * a given [Checkpoint]
 */
interface FlowStackServiceFactory {

    /**
     * @param checkpoint the [Checkpoint] for which the [FlowStackService] will be created.
     * @return returns a new instance of the [FlowStackService]
     */
    fun create(checkpoint: Checkpoint): FlowStackService
}