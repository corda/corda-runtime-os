package net.corda.flow.manager.impl

import co.paralleluniverse.fibers.FiberScheduler
import net.corda.data.flow.Checkpoint
import net.corda.data.flow.event.P2PMessage
import net.corda.flow.manager.FlowManager
import net.corda.flow.manager.FlowResult
import net.corda.internal.di.DependencyInjectionService
import net.corda.v5.application.flows.StateMachineRunId
import net.corda.v5.application.identity.Party
import net.corda.v5.application.services.IdentityService
import net.corda.v5.application.services.persistence.PersistenceService
import net.corda.v5.application.services.serialization.SerializationService
import java.util.concurrent.ScheduledExecutorService

data class FlowTopics(
    val flowEventTopic: String,
    val checkpointsTopic: String,
    val p2pOutTopic: String,
    val rpcResponseTopic: String,
    val flowSessionMappingTopic: String
)

class FlowManagerImpl : FlowManager {

    val checkpointSerialisationService: SerializationService
        get() = TODO("Not yet implemented")
    val persistenceService: PersistenceService
        get() = TODO("Not yet implemented")
    val dependencyInjector: DependencyInjectionService
        get() = TODO("Not yet implemented")
    val ourIdentity: Party
        get() = TODO("Not yet implemented")
    val flowExecutor: ScheduledExecutorService
        get() = TODO("Not yet implemented")
    val fiberScheduler: FiberScheduler
        get() = TODO("Not yet implemented")
    val identityService: IdentityService
        get() = TODO("Not yet implemented")

    override fun startInitiatingFlow(
        newFlowId: StateMachineRunId,
        clientId: String,
        flowName: String,
        args: List<Any?>
    ): FlowResult {
        TODO("Not yet implemented")
    }

    override fun startRemoteInitiatedFlow(
        newFlowId: StateMachineRunId,
        p2pMessage: P2PMessage
    ): FlowResult {
        TODO("Not yet implemented")
    }

    override fun wakeFlow(
        lastCheckpoint: Checkpoint
    ): FlowResult {
        TODO("Not yet implemented")
    }
}
