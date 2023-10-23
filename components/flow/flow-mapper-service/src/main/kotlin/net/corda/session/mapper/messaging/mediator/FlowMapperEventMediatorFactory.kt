package net.corda.session.mapper.messaging.mediator

import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.libs.configuration.SmartConfig
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
     */
    fun create(
        flowConfig: SmartConfig,
        messagingConfig: SmartConfig,
    ): MultiSourceEventMediator<String, FlowMapperState, FlowMapperEvent>

}