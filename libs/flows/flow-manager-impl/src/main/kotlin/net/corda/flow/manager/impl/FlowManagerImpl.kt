package net.corda.flow.manager.impl

import co.paralleluniverse.concurrent.util.ScheduledSingleThreadExecutor
import co.paralleluniverse.fibers.FiberExecutorScheduler
import net.corda.data.flow.Checkpoint
import net.corda.data.flow.event.FlowSessionMessage
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.manager.FlowManager
import net.corda.flow.manager.FlowResult
import net.corda.flow.statemachine.HousekeepingState
import net.corda.flow.statemachine.NonSerializableState
import net.corda.flow.statemachine.impl.FlowStateMachineImpl
import net.corda.internal.di.DependencyInjectionService
import net.corda.sandbox.cache.FlowMetadata
import net.corda.sandbox.cache.SandboxCache
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.identity.Party
import net.corda.v5.application.services.IdentityService
import net.corda.v5.application.services.persistence.PersistenceService
import net.corda.v5.application.services.serialization.SerializationService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.uncheckedCast
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Clock

//data class FlowTopics(
//    val flowEventTopic: String,
//    val checkpointsTopic: String,
//    val p2pOutTopic: String,
//    val rpcResponseTopic: String,
//    val flowSessionMappingTopic: String
//)

@Component
class FlowManagerImpl @Activate constructor(
    @Reference
    private val sandboxCache: SandboxCache,
    @Reference
    private val identityService: IdentityService,
    @Reference
    private val checkpointSerialisationService: SerializationService,
    @Reference
    private val dependencyInjector: DependencyInjectionService,
) : FlowManager {

    companion object {
        val log = contextLogger()
    }

    private val scheduler = FiberExecutorScheduler("Same thread scheduler", ScheduledSingleThreadExecutor())

    private val persistenceService: PersistenceService
        get() = TODO("Not yet implemented")
    private val ourIdentity: Party
        get() = TODO("Not yet implemented")

    override fun startInitiatingFlow(
        newFlowMetadata: FlowMetadata,
        clientId: String,
        args: List<Any?>
    ): FlowResult {
        log.info("start new flow clientId: $clientId flowName: ${newFlowMetadata.name} args $args")

        val flow = getOrCreate(newFlowMetadata.key.identity, newFlowMetadata, args)
        val stateMachine = FlowStateMachineImpl(
            clientId,
            newFlowMetadata.key,
            flow,
            ourIdentity,
            scheduler,
        )

        stateMachine.housekeepingState = HousekeepingState(
            0,
            ourIdentity,
            false,
            mutableListOf()
        )
        setupFlow(stateMachine)
        stateMachine.startFlow()
        val checkpoint = stateMachine.waitForCheckpoint()

        return FlowResult(
            checkpoint,
            emptyList()
        )
    }

    override fun startRemoteInitiatedFlow(
        newFlowMetadata: FlowMetadata,
        flowSessionMessage: FlowSessionMessage
    ): FlowResult {
        TODO("Not yet implemented")
    }

    override fun wakeFlow(
        lastCheckpoint: Checkpoint
    ): FlowResult {
        TODO("Not yet implemented")
    }

    private fun getOrCreate(identity: HoldingIdentity, flow: FlowMetadata, args: List<Any?>): Flow<*> {
        val flowClazz: Class<Flow<*>> =
            uncheckedCast(sandboxCache.getSandboxGroupFor(identity, flow).loadClass(flow.name, Flow::class.java))
        return flowClazz.getDeclaredConstructor().newInstance(args)
    }

    private fun setupFlow(flow: FlowStateMachineImpl<*>) {
        flow.nonSerializableState = NonSerializableState(
            checkpointSerialisationService,
            Clock.systemUTC()
        )
        dependencyInjector.injectDependencies(flow.logic, flow)
    }
}
