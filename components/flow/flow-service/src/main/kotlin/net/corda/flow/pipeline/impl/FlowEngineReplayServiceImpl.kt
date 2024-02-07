package net.corda.flow.pipeline.impl

import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.output.FlowStatus
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.flow.state.checkpoint.SavedOutput
import net.corda.data.flow.state.checkpoint.SavedOutputs
import net.corda.flow.pipeline.FlowEngineReplayService
import net.corda.messaging.api.records.Record
import org.osgi.service.component.annotations.Component

@Component(service = [FlowEngineReplayService::class])
class FlowEngineReplayServiceImpl : FlowEngineReplayService {
    companion object {
        //This list should match the types in Flow Event Mediator Message Router
        val ASYNC_PAYLOAD_TYPES = setOf(FlowMapperEvent::class.java, String::class.java, FlowStatus::class.java)
    }

    override fun getReplayEvents(
        inputEventHash: String,
        checkpoint: Checkpoint?
    ): List<Record<*, *>>? {
        return checkpoint?.savedOutputs
            ?.asReversed()
            ?.find { it.inputEventHash == inputEventHash }
            ?.outputEvents
            ?.map { Record(it.topic, it.key, it.payload) }
    }

    override fun generateSavedOutputs(inputEventHash: String, outputRecords: List<Record<*, *>>): SavedOutputs {
        val savedOutputList = outputRecords.filter { (it.value) != null && ASYNC_PAYLOAD_TYPES.contains(it.value!!::class.java)  }.map {
            SavedOutput(it.topic, it.key, it.value)
        }
        return SavedOutputs(inputEventHash, savedOutputList)
    }
}
