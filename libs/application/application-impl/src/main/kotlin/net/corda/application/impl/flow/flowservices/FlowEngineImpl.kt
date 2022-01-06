package net.corda.application.impl.flow.flowservices

import net.corda.flow.manager.fiber.FlowFiberExecutionContext
import net.corda.flow.manager.fiber.FlowFiberService
import net.corda.flow.manager.fiber.FlowIORequest
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowExternalOperation
import net.corda.v5.application.flows.FlowId
import net.corda.v5.application.flows.flowservices.FlowEngine
import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Duration

@Component(service = [SingletonSerializeAsToken::class])
class FlowEngineImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService
) : FlowEngine, SingletonSerializeAsToken, CordaFlowInjectable {

    private companion object {
        val log = contextLogger()
    }

    override val flowId: FlowId
        get() = flowFiberService.getExecutingFiber().flowId

    override val isKilled: Boolean
        get() = false

    @Suspendable
    override fun <R : Any> await(operation: FlowExternalOperation<R>): R {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun checkFlowIsNotKilled() {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun checkFlowIsNotKilled(lazyMessage: () -> Any) {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun sleep(duration: Duration) {
        TODO("Not yet implemented")
    }

    @Suspendable
    override fun <R> subFlow(subLogic: Flow<R>): R {
        log.info("Starting subflow ('${subLogic.javaClass.name}')...")

        getFiberExecutionContext().sandboxDependencyInjector.injectServices(subLogic)
        getFiberExecutionContext().flowStackService.push(subLogic)

        try {
            log.info("Calling sub-flow('${subLogic.javaClass.name}')...")
            val result = subLogic.call()
            log.info("Sub-flow('${subLogic.javaClass.name}') call completed ...")
            /*
             * TODOs:
             * Once the session management has been implemented we can look at optimising this, only calling
             * suspend for flows that require session cleanup
             */
            log.info("Suspending sub-flow('${subLogic.javaClass.name}')...")
            flowFiberService
                .getExecutingFiber()
                .suspend(FlowIORequest.SubFlowFinished(getFiberExecutionContext().flowStackService.pop()))
            log.info("Sub-flow('${subLogic.javaClass.name}') resumed .")
            return result
        } catch (e: Throwable) {
            flowFiberService
                .getExecutingFiber()
                .suspend(FlowIORequest.SubFlowFailed(e, getFiberExecutionContext().flowStackService.pop()))
            throw e
        }
    }

    private fun getFiberExecutionContext(): FlowFiberExecutionContext {
        return flowFiberService
            .getExecutingFiber()
            .getExecutionContext()
    }
}