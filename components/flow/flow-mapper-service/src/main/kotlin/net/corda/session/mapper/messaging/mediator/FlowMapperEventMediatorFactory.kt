package net.corda.session.mapper.messaging.mediator

import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.mediator.MultiSourceEventMediator

/**
 * Creates a Multi-Source Event Mediator for FlowMapper.
 */
interface FlowMapperEventMediatorFactory {
    /**
     * Creates a Multi-Source Event Mediator for FlowMapper.
     *
     * @param flowConfig Flow configuration.
     * @param messagingConfig Messaging configuration.
     * @param stateManager State manager.
     */
    fun create(
        flowConfig: SmartConfig,
        messagingConfig: SmartConfig,
        bootConfig: SmartConfig,
        stateManager: StateManager,
    ): MultiSourceEventMediator<String, FlowMapperState, FlowMapperEvent>

}