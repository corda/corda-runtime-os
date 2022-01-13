package net.corda.flow.manager

import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.messaging.api.processor.StateAndEventProcessor

interface FlowEventProcessor : StateAndEventProcessor<FlowKey, Checkpoint, FlowEvent>