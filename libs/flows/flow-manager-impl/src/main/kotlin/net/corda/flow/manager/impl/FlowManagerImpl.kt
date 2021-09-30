package net.corda.flow.manager.impl

import co.paralleluniverse.concurrent.util.ScheduledSingleThreadExecutor
import co.paralleluniverse.fibers.FiberExecutorScheduler
import net.corda.data.flow.Checkpoint
import net.corda.data.flow.FlowKey
import net.corda.data.flow.RPCFlowResult
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.FlowSessionMessage
import net.corda.data.flow.event.Wakeup
import net.corda.dependency.injection.DependencyInjectionService
import net.corda.flow.manager.FlowManager
import net.corda.flow.manager.FlowResult
import net.corda.flow.statemachine.FlowStateMachine
import net.corda.flow.statemachine.HousekeepingState
import net.corda.flow.statemachine.NonSerializableState
import net.corda.flow.statemachine.factory.FlowStateMachineFactory
import net.corda.messaging.api.records.Record
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.CheckpointSerializer
import net.corda.serialization.factory.CheckpointSerializerBuilderFactory
import net.corda.v5.application.flows.Flow
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Clock

@Component(service = [FlowManager::class])
class FlowManagerImpl @Activate constructor(
    @Reference(service = CheckpointSerializerBuilderFactory::class)
    private val checkpointSerializerBuilderFactory: CheckpointSerializerBuilderFactory,
    @Reference(service = DependencyInjectionService::class)
    private val dependencyInjector: DependencyInjectionService,
    @Reference(service = FlowStateMachineFactory::class)
    private val flowStateMachineFactory: FlowStateMachineFactory
) : FlowManager {

    companion object {
        val log = contextLogger()
    }

    private val scheduler = FiberExecutorScheduler("Same thread scheduler", ScheduledSingleThreadExecutor())
    private var checkpointSerializer : CheckpointSerializer? = null

    // Should be set up by the configration service
    private val resultTopic = ""
    private val wakeupTopic = ""
    private val deadLetterTopic = ""

    override fun startInitiatingFlow(
        flow: Flow<*>,
        flowName: String,
        flowKey: FlowKey,
        clientId: String,
        sandboxGroup: SandboxGroup,
        args: List<Any?>
    ): FlowResult {
        log.info("start new flow clientId: $clientId flowName: $flow args $args")

        val stateMachine = flowStateMachineFactory.createStateMachine(
            clientId,
            flowKey,
            flow,
//            ourIdentity,
            scheduler,
        )

        stateMachine.housekeepingState(
            HousekeepingState(
                0,
//            ourIdentity,
                false,
                mutableListOf()
            )
        )
        setupFlow(stateMachine, sandboxGroup)
        stateMachine.startFlow()
        val (checkpoint, events) = stateMachine.waitForCheckpoint()

        return FlowResult(
            checkpoint,
            events.toRecordsWithKey(flowName)
        )
    }

    override fun startRemoteInitiatedFlow(
        flow: Flow<*>,
        flowSessionMessage: FlowSessionMessage
    ): FlowResult {
        TODO("Not yet implemented")
    }

    override fun wakeFlow(
        lastCheckpoint: Checkpoint
    ): FlowResult {
        TODO("Not yet implemented")
    }

    private fun setupFlow(flow: FlowStateMachine<*>, sandboxGroup: SandboxGroup) {
        val serializerBuilder = checkpointSerializerBuilderFactory.createCheckpointSerializerBuilder(sandboxGroup)
        val checkpointSerializer = serializerBuilder.build()
        with(flow) {
            nonSerializableState(
                NonSerializableState(
                    checkpointSerializer,
                    Clock.systemUTC()
                )
            )
        }
        dependencyInjector.injectDependencies(flow.getFlowLogic(), flow)
    }

    private fun List<FlowEvent>.toRecordsWithKey(key: String): List<Record<String, ByteArray>> {
        return emptyList()
        return this.map { event ->
            val outputTopic = when (event.payload) {
                is RPCFlowResult -> resultTopic
                is Wakeup -> wakeupTopic
                else -> {
                    log.error("No topic available for $event")
                    deadLetterTopic
                }
            }
            Record(
                outputTopic,
                key,
                checkpointSerializer?.serialize(event)
            )
        }
    }
}
