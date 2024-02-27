package net.corda.flow.testing.fakes

import co.paralleluniverse.fibers.FiberScheduler
import net.corda.flow.fiber.ClientStartedFlow
import net.corda.flow.fiber.FiberFuture
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.fiber.FlowLogicAndArgs
import net.corda.flow.fiber.Interruptable
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

    val fiber = FakeFiber(UUID(0, 0), ClientStartedFlow(FakeFlow(), FakeClientRequestBody()))

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

        private var ioToCompleteWith: List<FlowIORequest<*>> = emptyList()
        var startContext: FlowFiberExecutionContext? = null
        var flowContinuation: FlowContinuation? = null
        private var ioRequestIterator = ioToCompleteWith.iterator()

        fun reset() {
            ioToCompleteWith = emptyList()
            startContext = null
            flowContinuation = null
            ioRequestIterator = ioToCompleteWith.iterator()
        }

        fun setIoRequests(requests: List<FlowIORequest<*>>) {
            ioToCompleteWith = requests
            ioRequestIterator = ioToCompleteWith.iterator()
        }

        override fun getExecutionContext(): FlowFiberExecutionContext {
            TODO("Not needed")
        }

        override fun getSandboxGroupId(): UUID? {
            TODO("Not needed")
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
            TODO("Not needed")
        }

        fun resume(
            suspensionOutcome: FlowContinuation
        ): Future<FlowIORequest<*>> {
            flowContinuation = suspensionOutcome
            return getCompletedFuture()
        }

        override fun <SUSPENDRETURN> suspend(request: FlowIORequest<SUSPENDRETURN>): SUSPENDRETURN {
            TODO("Not needed")
        }

        override fun attemptInterrupt() {
            TODO("Not needed")
        }

        private fun getCompletedFuture(): Future<FlowIORequest<*>> {
            val future = CompletableFuture<FlowIORequest<*>>()
            val ioRequest = try {
                ioRequestIterator.next()
            } catch (e: Exception) {
                throw IllegalStateException("No FlowIORequest associated with this test run", e)
            }
            future.complete(ioRequest)
            return future
        }
    }
}