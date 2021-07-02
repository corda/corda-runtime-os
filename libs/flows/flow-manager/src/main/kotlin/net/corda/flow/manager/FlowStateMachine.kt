package net.corda.flow.manager


import net.corda.internal.application.context.InvocationContext
import net.corda.v5.application.flows.Destination
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.StateMachineRunId
import net.corda.v5.application.identity.Party
import net.corda.v5.application.services.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.Logger
import java.time.Clock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService

interface FlowStateMachineHandle<FLOWRETURN> {
    val logic: Flow<FLOWRETURN>?
    val id: StateMachineRunId
    val resultFuture: CompletableFuture<FLOWRETURN>
    val clientId: String?
}

/** This is an internal interface that is implemented by code in the node module. You should look at [FlowLogic]. */
interface FlowStateMachine<FLOWRETURN> : FlowStateMachineHandle<FLOWRETURN> {
    @Suspendable
    fun <SUSPENDRETURN : Any> suspend(
        ioRequest: FlowIORequest<SUSPENDRETURN>,
        maySkipCheckpoint: Boolean
    ): SUSPENDRETURN

    @Suspendable
    fun initiateFlow(destination: Destination, wellKnownParty: Party): FlowSession

    @Suspendable
    fun <SUBFLOWRETURN> subFlow(currentFlow: Flow<*>, subFlow: Flow<SUBFLOWRETURN>): SUBFLOWRETURN

    fun updateTimedFlowTimeout(timeoutSeconds: Long)

    val logger: Logger
    val context: InvocationContext
    val ourIdentity: Party
    val creationTime: Long
    val isKilled: Boolean
    val clock: Clock
    val executor: ScheduledExecutorService
    val serializationService: SerializationService
}