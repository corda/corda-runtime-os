package net.corda.flow.manager.impl

import co.paralleluniverse.concurrent.util.ScheduledSingleThreadExecutor
import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberExecutorScheduler
import net.corda.data.flow.Checkpoint
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.FlowSessionMessage
import net.corda.dependency.injection.DependencyInjectionService
import net.corda.flow.manager.FlowManager
import net.corda.flow.manager.FlowMetaData
import net.corda.flow.manager.FlowResult
import net.corda.flow.statemachine.FlowStateMachine
import net.corda.flow.statemachine.HousekeepingState
import net.corda.flow.statemachine.NonSerializableState
import net.corda.flow.statemachine.factory.FlowStateMachineFactory
import net.corda.flow.statemachine.impl.FlowStateMachineImpl
import net.corda.messaging.api.records.Record
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.CheckpointSerializer
import net.corda.serialization.factory.CheckpointSerializerBuilderFactory
import net.corda.v5.application.flows.Flow
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.uncheckedCast
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
    private var checkpointSerializers = mutableMapOf<SandboxGroup, CheckpointSerializer>()

    override fun startInitiatingFlow(
        flowMetaData: FlowMetaData,
        clientId: String,
        sandboxGroup: SandboxGroup
    ): FlowResult {
        val flowName = flowMetaData.flowName
        val jsonArg = flowMetaData.jsonArg
        log.info("start new flow clientId: $clientId flowName: $flowName args $jsonArg")
        val flow =  getOrCreate(sandboxGroup, flowName, jsonArg)
        val stateMachine = flowStateMachineFactory.createStateMachine(
            clientId,
            flowMetaData.flowKey,
            flow,
            flowMetaData.cpiId,
            flowName,
            scheduler,
        )

        // Should this setting of the [houseKeeping] state be contained in [FlowStateMachineFactory.createStateMachine]? Or should these
        // be the _default_ values for this property? I think the state should be kept internal to the state machine module if possible.
        // Possible the factory should have multiple create methods to mimic the different start flow methods in this class.
        stateMachine.housekeepingState(
            HousekeepingState(
                0,
                false,
                mutableListOf()
            )
        )
        setupFlow(stateMachine, sandboxGroup)
        stateMachine.startFlow()
        val (checkpoint, events) = stateMachine.waitForCheckpoint()

        return FlowResult(
            checkpoint,
            events.toRecordsWithKey(flowMetaData.flowEventTopic)
        )
    }

    override fun startRemoteInitiatedFlow(
        flowMetaData: FlowMetaData,
        flowSessionMessage: FlowSessionMessage
    ): FlowResult {
        TODO("Not yet implemented")
    }

    override fun wakeFlow(
        lastCheckpoint: Checkpoint,
        wakeupEvent: FlowEvent,
        flowEventTopic: String,
        sandboxGroup: SandboxGroup,
    ): FlowResult {
        // could this become a alternative factory method?
        // we can then move the addition of the housekeeping state into the statemachine module
        val flowStateMachine = getCheckpointSerializer(sandboxGroup).deserialize(
            lastCheckpoint.fiber.array(),
            FlowStateMachineImpl::class.java
        )

        val flowState = lastCheckpoint.flowState
        val flowEvents = flowState.eventQueue
        flowEvents.add(wakeupEvent)
        val housekeepingState = HousekeepingState(flowState.suspendCount, flowState.isKilled, flowEvents)
        flowStateMachine.housekeepingState(housekeepingState)

        setupFlow(flowStateMachine, sandboxGroup)
        // Make this a function on [FlowStateMachine]
        Fiber.unparkDeserialized(flowStateMachine, scheduler)
        val (checkpoint, events) = flowStateMachine.waitForCheckpoint()
        return FlowResult(checkpoint, events.toRecordsWithKey(flowEventTopic))
    }

    // Doesn't seem like this function needs to be called [getOrCreate]
    private fun getOrCreate(sandboxGroup: SandboxGroup, flowName: String, jsonArg: String?): Flow<*> {
        val flowClazz: Class<Flow<*>> =
            uncheckedCast(sandboxGroup.loadClassFromMainBundles(flowName, Flow::class.java))
        val constructor = flowClazz.getDeclaredConstructor(String::class.java)
        return constructor.newInstance(jsonArg)
    }

    // can this also be contained within the factory method?
    private fun setupFlow(flow: FlowStateMachine<*>, sandboxGroup: SandboxGroup) {
        val checkpointSerializer = getCheckpointSerializer(sandboxGroup)

        with(flow) {
            nonSerializableState(
                NonSerializableState(
                    checkpointSerializer,
                    Clock.systemUTC()
                )
            )
        }

        // Maybe this part doesn't go into the factory, however it might also work inside the factory.
        dependencyInjector.injectDependencies(flow.getFlowLogic(), flow)
    }

    private fun getCheckpointSerializer(sandboxGroup: SandboxGroup): CheckpointSerializer {
        return checkpointSerializers.computeIfAbsent(sandboxGroup) {
            checkpointSerializerBuilderFactory.createCheckpointSerializerBuilder(it).build()
        }
    }

    private fun List<FlowEvent>.toRecordsWithKey(flowEventTopic: String): List<Record<FlowKey, FlowEvent>> {
        return this.map { event ->
            Record(
                flowEventTopic,
                event.flowKey,
                event
            )
        }
    }
}
