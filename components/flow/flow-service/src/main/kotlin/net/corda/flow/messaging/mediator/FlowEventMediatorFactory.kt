package net.corda.flow.messaging.mediator

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.messaging.mediator.fakes.TestMessageBus
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.mediator.MultiSourceEventMediator

/**
 * Creates a Multi-Source Event Mediator for flow engine.
 */
interface FlowEventMediatorFactory {
    fun create(
        configs: Map<String, SmartConfig>,
        messageBus: TestMessageBus,
        stateManager: StateManager,
    ): MultiSourceEventMediator<String, Checkpoint, FlowEvent>

}