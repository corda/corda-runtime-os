package net.corda.flow.pipeline

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.messaging.api.processor.StateAndEventProcessor

interface FlowEventProcessor : StateAndEventProcessor<String, Checkpoint, FlowEvent>