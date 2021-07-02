package net.corda.flow.manager

import co.paralleluniverse.fibers.FiberScheduler
import net.corda.internal.di.DependencyInjectionService
import net.corda.v5.application.flows.StateMachineRunId
import net.corda.v5.application.identity.Party
import net.corda.v5.application.services.persistence.PersistenceService
import net.corda.v5.application.services.serialization.SerializationService
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.concurrent.ScheduledExecutorService


data class FlowTopics(
    val flowEventTopic: String,
    val checkpointsTopic: String,
    val p2pOutTopic: String,
    val rpcResponseTopic: String,
    val flowSessionMappingTopic: String
)

data class FlowResult(
    val checkpoint: Checkpoint,
    val events: List<ProducerRecord<String, ByteArray?>>
)

interface FlowManager {
    val ourIdentity: Party
    val flowFactory: FlowFactory
    val dependencyInjector: DependencyInjectionService
    val flowExecutor: ScheduledExecutorService
    val fiberScheduler: FiberScheduler
    val checkpointSerialisationService: SerializationService
    val networkMapCache: NetworkMapCache
    val persistenceService: PersistenceService

    fun startRPCFlow(
        newFlowId: StateMachineRunId,
        clientId: String,
        flowName: String,
        args: List<Any?>,
        topics: FlowTopics
    ): FlowResult

    fun initiateRemoteFlow(
        newFlowId: StateMachineRunId,
        p2pMessage: FlowEvent.P2PMessage,
        topics: FlowTopics
    ): FlowResult

    fun runFlow(
        lastCheckpoint: Checkpoint,
        event: FlowEvent,
        topics: FlowTopics
    ): FlowResult
}