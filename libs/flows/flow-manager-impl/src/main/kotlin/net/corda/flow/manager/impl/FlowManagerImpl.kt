package net.corda.flow.manager.impl

import co.paralleluniverse.concurrent.util.ScheduledSingleThreadExecutor
import co.paralleluniverse.fibers.FiberExecutorScheduler
import net.corda.data.flow.Checkpoint
import net.corda.data.flow.event.FlowSessionMessage
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.manager.FlowManager
import net.corda.flow.manager.FlowResult
import net.corda.flow.statemachine.FlowIORequest
import net.corda.flow.statemachine.FlowStateMachine
import net.corda.flow.statemachine.impl.FlowStateMachineImpl
import net.corda.sandbox.cache.FlowId
import net.corda.sandbox.cache.SandboxCache
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowSession
import net.corda.v5.application.identity.CordaX500Name
import net.corda.v5.application.identity.Party
import net.corda.v5.application.services.IdentityService
import net.corda.v5.application.services.persistence.PersistenceService
import net.corda.v5.application.services.serialization.SerializationService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.uncheckedCast
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.CompletableFuture

data class FlowTopics(
    val flowEventTopic: String,
    val checkpointsTopic: String,
    val p2pOutTopic: String,
    val rpcResponseTopic: String,
    val flowSessionMappingTopic: String
)

class FlowManagerImpl @Activate constructor(
    @Reference
    private val sandboxCache: SandboxCache,
    @Reference
    private val identityService: IdentityService,
    ) : FlowManager {

    companion object {
        val log = contextLogger()
    }

    private val scheduler = FiberExecutorScheduler("Same thread scheduler", ScheduledSingleThreadExecutor())

    private val checkpointSerialisationService: SerializationService
        get() = TODO("Not yet implemented")
    private val persistenceService: PersistenceService
        get() = TODO("Not yet implemented")
    private val ourIdentity: Party
        get() = TODO("Not yet implemented")

    override fun startInitiatingFlow(
        newFlowId: FlowId,
        clientId: String,
        args: List<Any?>
    ): FlowSession {
        log.info("start new flow clientId: $clientId flowName: ${newFlowId.name} args $args")

        val sandboxIdentity = HoldingIdentity(clientId, "groupId")

        val stateMachine = FlowStateMachineImpl(
            clientId,
            net.corda.v5.application.flows.FlowId(UUID.randomUUID()),
            getOrCreate(sandboxIdentity, newFlowId, args),
            CompletableFuture(),
            LoggerFactory.getLogger("Flow $newFlowId log"),
            ourIdentity,
            false,
            UUID.randomUUID().toString(),
            scheduler,
        ) as FlowStateMachine<*>

        return stateMachine.initiateFlow(
            identityService.partyFromName(CordaX500Name.parse(""))!!,
            identityService.partyFromName(CordaX500Name.parse(""))!!
        ).also {
            // Trigger an initial snapshot
            stateMachine.suspend(FlowIORequest.ForceCheckpoint, false)
        }
    }

    override fun startRemoteInitiatedFlow(
        newFlowId: FlowId,
        flowSessionMessage: FlowSessionMessage
    ): FlowResult {
        TODO("Not yet implemented")
    }

    override fun wakeFlow(
        lastCheckpoint: Checkpoint
    ): FlowResult {
        TODO("Not yet implemented")
    }

    private fun getOrCreate(identity: HoldingIdentity, flow: FlowId, args: List<Any?>): Flow<*> {
        val flowClazz: Class<Flow<*>> =
            uncheckedCast(sandboxCache.getSandboxFor(identity, flow).loadClass(flow.name))
        return flowClazz.getDeclaredConstructor().newInstance(args)
    }
}
