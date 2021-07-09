package net.corda.flow.statemachine.impl


import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberScheduler
import net.corda.flow.statemachine.FlowStateMachine
import net.corda.internal.application.FlowIORequest
import net.corda.internal.application.context.InvocationContext
import net.corda.v5.application.flows.Destination
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowSession
import net.corda.v5.application.flows.FlowStackSnapshot
import net.corda.v5.application.flows.StateMachineRunId
import net.corda.v5.application.identity.Party
import net.corda.v5.serialization.SerializedBytes
import org.slf4j.Logger
import java.util.concurrent.CompletableFuture

@Suppress("LongParameterList")
class FlowStateMachineImpl<R>(
    override val clientId: String?,
    override val id: StateMachineRunId,
    override val logic: Flow<R>,
    scheduler: FiberScheduler,
    override val creationTime: Long = System.currentTimeMillis(), override val ourSenderUUID: String?,
    override val resultFuture: CompletableFuture<R>,
    override val logger: Logger,
    override val context: InvocationContext,
    override val ourIdentity: Party,
    override val isKilled: Boolean
) : Fiber<Unit>(id.toString(), scheduler), FlowStateMachine<R> {

    override fun <SUSPENDRETURN : Any> suspend(
        ioRequest: FlowIORequest<SUSPENDRETURN>,
        maySkipCheckpoint: Boolean
    ): SUSPENDRETURN {
        TODO("Not yet implemented")
    }

    override fun serialize(payloads: Map<FlowSession, Any>): Map<FlowSession, SerializedBytes<Any>> {
        TODO("Not yet implemented")
    }

    override fun initiateFlow(destination: Destination, wellKnownParty: Party): FlowSession {
        TODO("Not yet implemented")
    }

    override fun <SUBFLOWRETURN> subFlow(currentFlow: Flow<*>, subFlow: Flow<SUBFLOWRETURN>): SUBFLOWRETURN {
        TODO("Not yet implemented")
    }

    override fun flowStackSnapshot(flowClass: Class<out Flow<*>>): FlowStackSnapshot? {
        TODO("Not yet implemented")
    }

    override fun persistFlowStackSnapshot(flowClass: Class<out Flow<*>>) {
        TODO("Not yet implemented")
    }

    override fun updateTimedFlowTimeout(timeoutSeconds: Long) {
        TODO("Not yet implemented")
    }

}
