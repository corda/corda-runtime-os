package net.corda.flow.testing.fakes

import co.paralleluniverse.fibers.FiberScheduler
import net.corda.flow.fiber.FiberFuture
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.fiber.FlowLogicAndArgs
import net.corda.flow.fiber.Interruptable
import net.corda.flow.fiber.ClientStartedFlow
import net.corda.flow.fiber.factory.FlowFiberFactory
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [FlowFiberFactory::class, FakeFlowFiberFactory::class])
class FakeFlowFiberFactory : FlowFiberFactory {

    private class FakeInterruptable : Interruptable {
        override fun attemptInterrupt() {
            // do nothing
        }
    }

    val fiber = FakeFiber(UUID(0, 0), ClientStartedFlow(FakeFlow(), FakeRestRequestBody()))

    override fun createAndStartFlowFiber(
        flowFiberExecutionContext: FlowFiberExecutionContext,
        flowId: String,
        logic: FlowLogicAndArgs
    ): FiberFuture {
        return FiberFuture(FakeInterruptable(), fiber.startFlow(flowFiberExecutionContext))
    }

    override fun createAndResumeFlowFiber(
        flowFiberExecutionContext: FlowFiberExecutionContext,
        suspensionOutcome: FlowContinuation
    ): FiberFuture {
        return FiberFuture(FakeInterruptable(), fiber.resume(suspensionOutcome))
    }

    class FakeFiber(
        override val flowId: UUID,
        override val flowLogic: FlowLogicAndArgs
    ) : FlowFiber {

        var ioToCompleteWith: FlowIORequest<*>? = null
        var startContext: FlowFiberExecutionContext? = null
        var flowContinuation: FlowContinuation? = null

        fun reset() {
            ioToCompleteWith = null
            startContext = null
            flowContinuation = null
        }

        override fun getExecutionContext(): FlowFiberExecutionContext {
            TODO("Not yet implemented")
        }

        override fun startFlow(flowFiberExecutionContext: FlowFiberExecutionContext): Future<FlowIORequest<*>> {
            startContext = flowFiberExecutionContext
            flowContinuation = FlowContinuation.Run(Unit)
            return getCompletedFuture()
        }

        override fun resume(
            flowFiberExecutionContext: FlowFiberExecutionContext,
            suspensionOutcome: FlowContinuation,
            scheduler: FiberScheduler
        ): Future<FlowIORequest<*>> {
            TODO("Not yet implemented")
        }

        fun resume(
            suspensionOutcome: FlowContinuation
        ): Future<FlowIORequest<*>> {
            flowContinuation = suspensionOutcome
            return getCompletedFuture()
        }

        override fun <SUSPENDRETURN> suspend(request: FlowIORequest<SUSPENDRETURN>): SUSPENDRETURN {
            TODO("Not yet implemented")
        }

        private fun getCompletedFuture(): Future<FlowIORequest<*>> {
            val future = CompletableFuture<FlowIORequest<*>>()
            future.complete(checkNotNull(ioToCompleteWith) { "No FlowIORequest associated with this test run" })
            ioToCompleteWith = null
            return future
        }
    }
}