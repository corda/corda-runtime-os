package net.corda.flow.application.services

import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.state.FlowStackItem
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.security.AccessController
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction
import java.util.UUID

@Component(service = [FlowEngine::class, SingletonSerializeAsToken::class], scope = PROTOTYPE)
class FlowEngineImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService
) : FlowEngine, SingletonSerializeAsToken {

    private companion object {
        val log = contextLogger()
    }

    override val flowId: UUID
        get() = flowFiberService.getExecutingFiber().flowId

    override val virtualNodeName: MemberX500Name
        get() = flowFiberService.getExecutingFiber().getExecutionContext().memberX500Name

    override val flowContextProperties: FlowContextProperties
        get() = flowFiberService.getExecutingFiber().getExecutionContext().flowCheckpoint.flowContext

    @Suspendable
    override fun <R> subFlow(subFlow: SubFlow<R>): R {

        val subFlowClassName = subFlow.javaClass.name

        log.debug { "Starting sub-flow ('$subFlowClassName')..." }

        try {
            AccessController.doPrivileged(PrivilegedExceptionAction {
                getFiberExecutionContext().sandboxGroupContext.dependencyInjector.injectServices(subFlow)
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
        getFiberExecutionContext().flowStackService.push(subFlow)

        try {
            log.debug { "Calling sub-flow('$subFlowClassName')..." }
            val result = subFlow.call()
            log.debug { "Sub-flow('$subFlowClassName') call completed ..." }
            /*
             * TODOs:
             * Once the session management has been implemented we can look at optimising this, only calling
             * suspend for flows that require session cleanup
             */
            log.debug { "Suspending sub-flow('$subFlowClassName')..." }

            finishSubFlow()

            log.debug { "Sub-flow('${subFlow.javaClass.name}') resumed ." }
            return result
        } catch (t: Throwable) {
            failSubFlow(t)
            throw t
        }
    }

    @Suspendable
    private fun finishSubFlow() {
        try {
            flowFiberService.getExecutingFiber().suspend(FlowIORequest.SubFlowFinished(peekCurrentFlowStackItem()))
        } finally {
            popCurrentFlowStackItem()
        }
    }

    @Suspendable
    private fun failSubFlow(t: Throwable) {
        try {
            flowFiberService.getExecutingFiber().suspend(FlowIORequest.SubFlowFailed(t, peekCurrentFlowStackItem()))
        } finally {
            popCurrentFlowStackItem()
        }
    }

    private fun peekCurrentFlowStackItem(): FlowStackItem {
        return getFiberExecutionContext().flowStackService.peek()
            ?: throw CordaRuntimeException(
                "Flow [${flowFiberService.getExecutingFiber().flowId}] does not have a flow stack item")
    }

    private fun popCurrentFlowStackItem(): FlowStackItem {
        return getFiberExecutionContext().flowStackService.pop()
            ?: throw CordaRuntimeException(
                "Flow [${flowFiberService.getExecutingFiber().flowId}] does not have a flow stack item")
    }

    private fun getFiberExecutionContext(): FlowFiberExecutionContext {
        return flowFiberService
            .getExecutingFiber()
            .getExecutionContext()
    }
}
