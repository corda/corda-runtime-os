package net.corda.flow.statemachine

import net.corda.data.flow.FlowKey
import net.corda.v5.application.flows.Destination
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowSession
import net.corda.v5.application.identity.Party
import net.corda.v5.application.services.serialization.SerializationService
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.Logger
import java.time.Instant
import java.util.concurrent.CompletableFuture


/**
 * Main data object representing snapshot of the flow stack, extracted from the Quasar stack.
 */
data class FlowStackSnapshot(
    val time: Instant,
    val flowClass: String,
    val stackFrames: List<Frame>
) {
    data class Frame(
        val stackTraceElement: StackTraceElement, // This should be the call that *pushed* the frame of [objects]
        val stackObjects: List<Any?>
    ) {
        override fun toString(): String = stackTraceElement.toString()
    }
}

@DoNotImplement
interface FlowStateMachineHandle<FLOWRETURN> {
    val logic: Flow<FLOWRETURN>?
    val id: FlowKey
    val resultFuture: CompletableFuture<FLOWRETURN>
    val clientId: String?
}

@DoNotImplement
interface FlowStateMachine<FLOWRETURN> : FlowStateMachineHandle<FLOWRETURN> {
    @Suspendable
    fun <SUSPENDRETURN : Any> suspend(ioRequest: FlowIORequest<SUSPENDRETURN>): SUSPENDRETURN

    @Suspendable
    fun initiateFlow(destination: Destination, wellKnownParty: Party): FlowSession

    @Suspendable
    fun <SUBFLOWRETURN> subFlow(currentFlow: Flow<*>, subFlow: Flow<SUBFLOWRETURN>): SUBFLOWRETURN

    fun updateTimedFlowTimeout(timeoutSeconds: Long)

    val logger: Logger
    val ourIdentity: Party
    val creationTime: Long
    val isKilled: Boolean
    val serializationService: SerializationService
}
