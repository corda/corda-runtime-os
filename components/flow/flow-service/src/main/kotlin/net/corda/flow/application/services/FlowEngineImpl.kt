package net.corda.flow.application.services

import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.flow.fiber.FlowContinuationErrorException
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberImpl
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
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
        } catch (e: FlowContinuationErrorException) {
            // Logging the callstack here would be misleading as it would point the log entry to the internal rethrow
            // in Corda rather than the code in the flow that failed
            log.warn("Sub-flow was discontinued, reason: ${e.cause?.javaClass?.canonicalName} thrown, ${e.cause?.message}")
            failSubFlow(e)
            throw e
        } catch (t: Throwable) {
            log.error("Sub-flow failed due to exception thrown", t)
            failSubFlow(t)
            throw t
        } finally {
            popCurrentFlowStackItem()
        }
    }

    @Suspendable
    private fun finishSubFlow() {
        flowFiberService.getExecutingFiber().suspend(FlowIORequest.SubFlowFinished(peekCurrentFlowStackItem()))
    }

    @Suspendable
    private fun failSubFlow(t: Throwable) {
        flowFiberService.getExecutingFiber().suspend(FlowIORequest.SubFlowFailed(t, peekCurrentFlowStackItem()))
    }

    private fun peekCurrentFlowStackItem(): FlowStackItem {
        return getFiberExecutionContext().flowStackService.peek()
            ?: throw CordaRuntimeException(
                "Flow [${flowFiberService.getExecutingFiber().flowId}] does not have a flow stack item"
            )
    }

    private fun popCurrentFlowStackItem(): FlowStackItem {
        return getFiberExecutionContext().flowStackService.pop()
            ?: throw CordaRuntimeException(
                "Flow [${flowFiberService.getExecutingFiber().flowId}] does not have a flow stack item"
            )
    }

    private fun getFiberExecutionContext(): FlowFiberExecutionContext {
        return flowFiberService
            .getExecutingFiber()
            .getExecutionContext()
    }
}
