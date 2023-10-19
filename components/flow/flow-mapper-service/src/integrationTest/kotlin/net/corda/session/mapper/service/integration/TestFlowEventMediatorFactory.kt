package net.corda.session.mapper.service.integration

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.mediator.MultiSourceEventMediator

interface TestFlowEventMediatorFactory {
    fun create(
        messagingConfig: SmartConfig,
        stateManagerConfig: SmartConfig,
        flowEventProcessor: TestFlowMessageProcessor,
    ): MultiSourceEventMediator<String, Checkpoint, FlowEvent>
}