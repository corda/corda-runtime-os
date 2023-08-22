package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
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
            getSessionsToCloseForWaitingFor(context.checkpoint, request)
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
            val initiatingAndInitiated = flowSessionManager.getInitiatingAndInitiatedSessions(sessionsToClose)
            val initiatingSessions = initiatingAndInitiated.first
            val initiatedSessions = initiatingAndInitiated.second

            // if I am the initiated party i.e ends with INITIATED_SESSION_ID_SUFFIX
            if(initiatedSessions.isNotEmpty()) {
                checkpoint.putSessionStates(flowSessionManager.sendCloseMessages(checkpoint, initiatedSessions, Instant.now()))
            }

            if (initiatingSessions.isNotEmpty()) {

            }
            /**
             *  if i am initiating party i.e i dont end with INITIATED_SESSION_ID_SUFFIX
                 if requireClose == false
                 - set status to CLOSED
                    - flowSessionManager.setStatus(sessionId, CLOSED)
                else if requireClose == true
                    if status == CLOSING
                        - set status closed
                    else
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

    private fun filterByState(checkpoint: FlowCheckpoint, sessions: List<String>): List<String> {
        val statusToFilterOut = setOf(SessionStateType.ERROR, SessionStateType.CLOSED)
        val statesInErrorOrClosed = flowSessionManager.getSessionsWithStatuses(checkpoint, sessions, statusToFilterOut)
        val sessionsInErrorOrClosed = statesInErrorOrClosed.map { it.sessionId }
        return sessions - sessionsInErrorOrClosed
    }

    private fun filterByRequireClose(checkpoint: FlowCheckpoint, sessions: List<String>): Pair<List<String>, List<String>>{
        val sessionsWithRequireCloseTrueAndFalse = flowSessionManager.getRequireCloseTrueAndFalse(checkpoint, sessions)
        val sessionsWithRequireCloseFalse = sessionsWithRequireCloseTrueAndFalse.second
        sessions -= sessionsWithRequireCloseFalse.map { it.sessionId }
    }

    private fun getSessionsToCloseForWaitingFor(
        checkpoint: FlowCheckpoint,
        request: FlowIORequest.CloseSessions
    ): List<String> {
        val sessions = getSessionsToClose(checkpoint, request)

        //filter out initiated
        val initiatingAndInitiated = flowSessionManager.getInitiatingAndInitiatedSessions(sessions)
        val initiatingSessions = initiatingAndInitiated.first

        //filter out ERROR or CLOSED
        val filteredSessions = filterByState(checkpoint, initiatingSessions)

        //filter out RequireClose false
        val requireCloseSessions = filterByRequireClose(checkpoint, filteredSessions)

        return requireCloseSessions.first
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
        return sessions
    }
}
