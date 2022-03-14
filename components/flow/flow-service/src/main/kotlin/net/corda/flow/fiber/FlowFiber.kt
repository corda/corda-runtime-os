package net.corda.flow.fiber

import co.paralleluniverse.fibers.FiberScheduler
import net.corda.data.flow.FlowKey
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowId
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import java.util.concurrent.Future

@DoNotImplement
interface FlowFiber<FLOWRESULT> {
    val flowId: FlowId
    val flowKey: FlowKey
    val flowLogic: Flow<FLOWRESULT>

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
