package net.corda.flow.worker

import net.corda.component.sandbox.FlowMetadata
import net.corda.component.sandbox.SandboxService
import net.corda.data.flow.Checkpoint
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartRPCFlow
import net.corda.data.flow.event.Wakeup
import net.corda.flow.manager.FlowManager
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.sandbox.SandboxGroup
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.application.flows.Flow

class FlowMessageProcessor(
    private val flowManager: FlowManager,
    private val sandboxService: SandboxService,
) : StateAndEventProcessor<FlowKey, Checkpoint, FlowEvent> {

    override fun onNext(
        state: Checkpoint?,
        event: Record<FlowKey, FlowEvent>
    ): StateAndEventProcessor.Response<Checkpoint> {
        val payload = event.value?.payload ?: throw IllegalArgumentException()
        val result = when (payload) {
            is StartRPCFlow -> {
                val flowName = payload.flowName
                //val cpiId = payload.cpiId
                val identity = event.key.identity
                val flowMetadata =  FlowMetadata(flowName, event.key)
                val sandboxGroup =  sandboxService.getSandboxGroupFor(identity, flowMetadata)
                val flow =  getOrCreate(sandboxGroup, flowMetadata, payload.args)

                flowManager.startInitiatingFlow(flow, flowName, event.key, payload.clientId, sandboxGroup, payload.args)
                //flowManager.startInitiatingFlow(sandboxService.getSandboxGroupFor(payload.cpiId), payload.clientId, payload.args)
            }
            is Wakeup -> {
                val checkpoint = state ?: throw IllegalArgumentException()
                flowManager.wakeFlow(checkpoint)
            }
            else -> {
                throw NotImplementedError()
            }
        }
        return StateAndEventProcessor.Response(result.checkpoint, result.events)
    }

    @Suppress("SpreadOperator")
    private fun getOrCreate(sandboxGroup: SandboxGroup, flow: FlowMetadata, args: List<Any?>): Flow<*> {
        val flowClazz: Class<Flow<*>> =
            uncheckedCast(sandboxGroup.loadClassFromCordappBundle(flow.name, Flow::class.java))
        val constructor = flowClazz.getDeclaredConstructor(*args.map { it!!::class.java }.toTypedArray())
        return constructor.newInstance(*args.toTypedArray())
    }

    override val keyClass = FlowKey::class.java
    override val stateValueClass = Checkpoint::class.java
    override val eventValueClass = FlowEvent::class.java
}