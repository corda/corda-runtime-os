package net.corda.flow.fiber

import co.paralleluniverse.fibers.FiberScheduler
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import java.util.UUID
import java.util.concurrent.Future

@DoNotImplement
interface FlowFiber: Interruptable {
    val flowId: UUID
    val flowLogic: FlowLogicAndArgs

    fun getExecutionContext(): FlowFiberExecutionContext

    fun startFlow(flowFiberExecutionContext: FlowFiberExecutionContext): Future<FlowIORequest<*>>

    fun resume(
        flowFiberExecutionContext: FlowFiberExecutionContext,
        suspensionOutcome: FlowContinuation,
        scheduler: FiberScheduler
    ): Future<FlowIORequest<*>>

    @Suspendable
    fun <SUSPENDRETURN> suspend(request: FlowIORequest<SUSPENDRETURN>): SUSPENDRETURN
}
