package net.corda.flow.statemachine

import net.corda.internal.application.FlowIORequest
import net.corda.internal.application.context.InvocationContext
import net.corda.internal.di.FlowStateMachineInjectable
import net.corda.v5.application.flows.Destination
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowSession
import net.corda.v5.application.flows.FlowStackSnapshot
import net.corda.v5.application.flows.StateMachineRunId
import net.corda.v5.application.identity.Party
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.serialization.SerializedBytes
import org.slf4j.Logger
import java.util.concurrent.CompletableFuture

@DoNotImplement
interface FlowStateMachineHandle<FLOWRETURN> {
    val logic: Flow<FLOWRETURN>?
    val id: StateMachineRunId
    val resultFuture: CompletableFuture<FLOWRETURN>
    val clientId: String?
}

@DoNotImplement
interface FlowStateMachine<FLOWRETURN> : FlowStateMachineHandle<FLOWRETURN>, FlowStateMachineInjectable {
    @Suspendable
    fun <SUSPENDRETURN : Any> suspend(ioRequest: FlowIORequest<SUSPENDRETURN>, maySkipCheckpoint: Boolean): SUSPENDRETURN

    fun serialize(payloads: Map<FlowSession, Any>): Map<FlowSession, SerializedBytes<Any>>

    @Suspendable
    fun initiateFlow(destination: Destination, wellKnownParty: Party): FlowSession

    @Suspendable
    fun <SUBFLOWRETURN> subFlow(currentFlow: Flow<*>, subFlow: Flow<SUBFLOWRETURN>): SUBFLOWRETURN

    @Suspendable
    fun flowStackSnapshot(flowClass: Class<out Flow<*>>): FlowStackSnapshot?

    @Suspendable
    fun persistFlowStackSnapshot(flowClass: Class<out Flow<*>>)

    fun updateTimedFlowTimeout(timeoutSeconds: Long)

    val logger: Logger
    val context: InvocationContext
    val ourIdentity: Party
    val ourSenderUUID: String?
    val creationTime: Long
    val isKilled: Boolean
}