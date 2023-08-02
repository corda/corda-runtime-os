package net.corda.flow.application.services.impl

import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.data.flow.state.checkpoint.FlowStackItemSession
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.application.versioning.impl.RESET_VERSIONING_MARKER
import net.corda.flow.application.versioning.impl.VERSIONING_PROPERTY_NAME
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.state.asFlowContext
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
            = getFiberExecutionContext().memberX500Name

    override fun getFlowContextProperties(): FlowContextProperties
            = getFiberExecutionContext().flowCheckpoint.flowContext

    @Suspendable
    override fun <R> subFlow(subFlow: SubFlow<R>): R {

        try {
            @Suppress("deprecation", "removal")
            java.security.AccessController.doPrivileged(PrivilegedExceptionAction {
                getFiberExecutionContext().sandboxGroupContext.dependencyInjector.injectServices(subFlow)
            })
        } catch (e: PrivilegedActionException) {
            throw e.exception
        }
        getFiberExecutionContext().flowStackService.push(subFlow, getFiberExecutionContext().flowMetrics)

        resetFlowVersioningInformationIfSubFlowIsInitiatingFlow()

        try {
            val result = subFlow.call()
            /*
             * TODOs:
             * Once the session management has been implemented we can look at optimising this, only calling
             * suspend for flows that require session cleanup
             */

            closeSessionsOnSubFlowFinish()
            getFiberExecutionContext().flowMetrics.subFlowFinished(FlowStates.COMPLETED)

            return result
        } catch (t: Throwable) {
            // Stack trace is filled in on demand. Without prodding that process, calls to suspend the flow will
            // serialize and deserialize and not reproduce the stack trace correctly.
            t.stackTrace
            // We cannot conclude that throwing an exception out of a sub-flow is an error. User code is free to do this
            // as long as it catches it in the flow which initiated it. The only thing Corda needs to do here is mark
            // the sub-flow as failed and rethrow.
            errorSessionsOnSubFlowFinish(t)
            getFiberExecutionContext().flowMetrics.subFlowFinished(FlowStates.FAILED)
            throw t
        } finally {
            popCurrentFlowStackItem()
        }
    }

    private fun resetFlowVersioningInformationIfSubFlowIsInitiatingFlow() {
        if (peekCurrentFlowStackItem().isInitiatingFlow) {
            // Cannot be put into [VersioningService] due to a circular dependency
            flowContextProperties[VERSIONING_PROPERTY_NAME]?.let {
                flowContextProperties.asFlowContext.platformProperties[VERSIONING_PROPERTY_NAME] = RESET_VERSIONING_MARKER
            }
        }
    }

    private val currentSessionIds: List<String>
        get() = peekCurrentFlowStackItem().sessions
            .filter(FlowStackItemSession::getInitiated)
            .map(FlowStackItemSession::getSessionId)

    @Suspendable
    private fun closeSessionsOnSubFlowFinish() {
        val currentSessionIds = this.currentSessionIds
        if (currentSessionIds.isNotEmpty() && anyActiveSessions(currentSessionIds)) {
            flowFiberService.getExecutingFiber()
                .suspend(FlowIORequest.SubFlowFinished(currentSessionIds))
        }
    }

    @Suspendable
    private fun errorSessionsOnSubFlowFinish(t: Throwable) {
        val currentSessionIds = this.currentSessionIds
        if (currentSessionIds.isNotEmpty() && anyActiveSessions(currentSessionIds)) {
           flowFiberService.getExecutingFiber()
               .suspend(FlowIORequest.SubFlowFailed(t, currentSessionIds))
        }
    }
    private fun anyActiveSessions(currentSessionIds: List<String>): Boolean {
        val flowCheckPoint = getFiberExecutionContext().flowCheckpoint
        return currentSessionIds.any {sessionId ->
            flowCheckPoint.getSessionState(sessionId)?.status !in listOf(SessionStateType.CLOSED, SessionStateType.ERROR)
        }
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
