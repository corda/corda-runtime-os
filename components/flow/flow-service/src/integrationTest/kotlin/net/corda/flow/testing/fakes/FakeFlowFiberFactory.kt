package net.corda.flow.testing.fakes

import co.paralleluniverse.fibers.FiberScheduler
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.fiber.factory.FlowFiberFactory
import net.corda.v5.application.flows.Flow
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [FlowFiberFactory::class, FakeFlowFiberFactory::class])
class FakeFlowFiberFactory : FlowFiberFactory {

    val fiber = FakeFiber<Any?>(UUID(0, 0), FakeFlow())

    override fun createFlowFiber(flowId: String, logic: Flow<*>, scheduler: FiberScheduler): FlowFiber<Any?> {
        return fiber
    }

    override fun createFlowFiber(flowFiberExecutionContext: FlowFiberExecutionContext): FlowFiber<*> {
        return fiber
    }

    class FakeFiber<R>(
        override val flowId: UUID,
        override val flowLogic: Flow<R>
    ) : FlowFiber<R> {

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
            return getCompletedFuture()
        }

        override fun resume(
            flowFiberExecutionContext: FlowFiberExecutionContext,
            suspensionOutcome: FlowContinuation,
            scheduler: FiberScheduler
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