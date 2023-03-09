@file:Suppress("deprecation")
package net.corda.flow.application.services

import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.security.AccessController
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction
import java.util.UUID

@Component(service = [ FlowEngine::class, UsedByFlow::class ], scope = PROTOTYPE)
class FlowEngineImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService
) : FlowEngine, UsedByFlow, SingletonSerializeAsToken {

    override fun getFlowId(): UUID
        = flowFiberService.getExecutingFiber().flowId

    override fun getVirtualNodeName(): MemberX500Name
        = flowFiberService.getExecutingFiber().getExecutionContext().memberX500Name

    override fun getFlowContextProperties(): FlowContextProperties
        = flowFiberService.getExecutingFiber().getExecutionContext().flowCheckpoint.flowContext

    @Suspendable
    override fun <R> subFlow(subFlow: SubFlow<R>): R {

        try {
            AccessController.doPrivileged(PrivilegedExceptionAction {
                getFiberExecutionContext().sandboxGroupContext.dependencyInjector.injectServices(subFlow)
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
        getFiberExecutionContext().flowStackService.push(subFlow)

        try {
            val result = subFlow.call()
            /*
             * TODOs:
             * Once the session management has been implemented we can look at optimising this, only calling
             * suspend for flows that require session cleanup
             */

            finishSubFlow()

            return result
        } catch (t: Throwable) {
            // Stack trace is filled in on demand. Without prodding that process, calls to suspend the flow will
            // serialize and deserialize and not reproduce the stack trace correctly.
            t.stackTrace
            // We cannot conclude that throwing an exception out of a sub-flow is an error. User code is free to do this
            // as long as it catches it in the flow which initiated it. The only thing Corda needs to do here is mark
            // the sub-flow as failed and rethrow.
            failSubFlow(t)
            throw t
        } finally {
            popCurrentFlowStackItem()
        }
    }

    @Suspendable
    private fun finishSubFlow() {
        flowFiberService
            .getExecutingFiber()
            .suspend(
                FlowIORequest.SubFlowFinished(
                    peekCurrentFlowStackItem()
                        .sessions
                        .filter { it.initiated }
                        .map { it.sessionId }
                        .toList()
                )
            )
    }

    @Suspendable
    private fun failSubFlow(t: Throwable) {
        flowFiberService
            .getExecutingFiber()
            .suspend(
                FlowIORequest.SubFlowFailed(
                    t,
                    peekCurrentFlowStackItem()
                        .sessions
                        .filter { it.initiated }
                        .map { it.sessionId }
                        .toList()
                )
            )
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
