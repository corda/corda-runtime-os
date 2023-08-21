package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.requests.helper.isInitiatedIdentity
import net.corda.flow.pipeline.handlers.requests.helper.isInitiatingIdentity
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.pipeline.sessions.FlowSessionStateException
import net.corda.flow.state.FlowCheckpoint
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant


@Component(service = [FlowRequestHandler::class])
class CloseSessionsRequestHandler @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory
) : FlowRequestHandler<FlowIORequest.CloseSessions> {

    override val type = FlowIORequest.CloseSessions::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.CloseSessions): WaitingFor {
        val sessionsToClose = try {
            getSessionsToClose(context.checkpoint, request)
        } catch (e: IllegalArgumentException) {
            // TODO Wakeup flow with an error
            throw FlowFatalException(
                e.message ?: "An error occurred in the platform - A session in ${request.sessions} was missing from the checkpoint",
                e
            )
        }
        return if (sessionsToClose.isEmpty()) {
            WaitingFor(net.corda.data.flow.state.waiting.Wakeup())
        } else {
            WaitingFor(SessionConfirmation(sessionsToClose, SessionConfirmationType.CLOSE))
        }
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.CloseSessions): FlowEventContext<Any> {
        val checkpoint = context.checkpoint

        val hasNoSessionsOrAllClosed = try {
            val sessionsToClose = getSessionsToClose(checkpoint, request)

            // if I am the initiated party i.e ends with INITIATED_SESSION_ID_SUFFIX
            if(sessionsToClose.isNotEmpty()) {
                checkpoint.putSessionStates(flowSessionManager.sendCloseMessages(checkpoint, sessionsToClose, Instant.now()))
            } else if (sessionsToClose.isNotEmpty()) {
                sessionsToClose = filterOutRequireClose(checkpoint, request, requireClose = false)
            }


            /**
             * else if i am initiating party i.e i dont end with INITIATED_SESSION_ID_SUFFIX
                 if requireClose == false
                 - set status to CLOSED
            - flowSessionManager.setStatus(sessionId, CLOSED)
            else if requireClose == true
                 - set status CLOSING]
                 - flowSessionManager.setStatus(sessionId, CLOSING)
             */

            sessionsToClose.isEmpty() || flowSessionManager.doAllSessionsHaveStatus(checkpoint, sessionsToClose, SessionStateType.CLOSED)
        } catch (e: FlowSessionStateException) {
            // TODO CORE-4850 Wakeup with error when session does not exist
            throw FlowFatalException(e.message, e)
        }

        return if (hasNoSessionsOrAllClosed) {
            val record = flowRecordFactory.createFlowEventRecord(checkpoint.flowId, Wakeup())
            context.copy(outputRecords = context.outputRecords + listOf(record))
        } else {
            context
        }
    }

    private fun getSessionsToClose(checkpoint: FlowCheckpoint, request: FlowIORequest.CloseSessions): List<String> {
        /**
         * else if i am initiating party i.e i dont end with INITIATED_SESSION_ID_SUFFIX
        if requireClose == false
        - dont return this session in the list
        else if requireClose == true
        - do return this session in the list

         && filter out sessions in status ERROR or CLOSED
         */

        val sessions = request.sessions.toMutableList()

        val statusToFilterOut = setOf(SessionStateType.ERROR, SessionStateType.CLOSED)
        val sessionsInErrorOrClosed = flowSessionManager.getSessionsWithStatuses(checkpoint, sessions, statusToFilterOut)
        sessions -=  sessionsInErrorOrClosed.map { it.sessionId }

        val sessionsWithRequireCloseFalse = flowSessionManager.getSessionsByRequireClose(checkpoint, sessions, requireClose = false)
        sessions -= sessionsWithRequireCloseFalse.map { it.sessionId }

        return sessions
    }
}
