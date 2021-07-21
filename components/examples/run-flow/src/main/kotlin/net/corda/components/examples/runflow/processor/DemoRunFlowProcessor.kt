package net.corda.components.examples.runflow.processor

import net.corda.data.client.rpc.flow.RPCFlowStart
import net.corda.data.flow.Checkpoint
import net.corda.data.flow.event.FlowEvent
import net.corda.flow.manager.FlowManager
import net.corda.flow.manager.FlowResult
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.sandbox.cache.FlowMetadata
import net.corda.v5.base.util.contextLogger

class DemoRunFlowProcessor(
    private val flowManager: FlowManager
) : StateAndEventProcessor<String, Checkpoint, FlowEvent> {

    private companion object {
        val log = contextLogger()
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val stateValueClass: Class<Checkpoint>
        get() = Checkpoint::class.java
    override val eventValueClass: Class<FlowEvent>
        get() = FlowEvent::class.java

    override fun onNext(
        state: Checkpoint?,
        event: Record<String, FlowEvent>
    ): StateAndEventProcessor.Response<Checkpoint> {
        log.info("Demo Run Flow Processor: key/value  ${event.key}/${event.value}")
        val flowMessage = event.value!!.payload //should be safe because payload can't be null
        val flowKey = event.value!!.flowKey
        val results = when (flowMessage) {
            is RPCFlowStart -> {
                val flowMetadata = FlowMetadata(
                    flowMessage.flowName,
                    flowKey
                )

                flowManager.startInitiatingFlow(
                    flowMetadata,
                    flowMessage.clientId,
                    flowMessage.args
                )
            }
            else -> FlowResult(null, emptyList())
        }

        return StateAndEventProcessor.Response(results.checkpoint, results.events)
    }
}
