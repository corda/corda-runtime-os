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

            /**
             *  if i am initiating party i.e i dont end with INITIATED_SESSION_ID_SUFFIX
            if requireClose == false
            - set status to CLOSED
            - flowSessionManager.setStatus(sessionId, CLOSED)
            else if requireClose == true
            - set status CLOSING]
            - flowSessionManager.setStatus(sessionId, CLOSING)
            else
            if status == CLOSING
            - set status closed

             */

            if (initiatingSessions.isNotEmpty()) {
                closeSessionsAlreadyInClosing(checkpoint, initiatingSessions)

                val requireCloseTrueAndFalse = flowSessionManager.getRequireCloseTrueAndFalse(checkpoint, initiatingSessions)
                val requireCloseTrue = requireCloseTrueAndFalse.first
                val requireCloseFalse = requireCloseTrueAndFalse.second

                if(requireCloseFalse.isNotEmpty()) {
                    flowSessionManager.updateStatus(checkpoint, requireCloseFalse, SessionStateType.CLOSED)
                }

                if (requireCloseTrue.isNotEmpty()) {
                    flowSessionManager.updateStatus(checkpoint, requireCloseFalse, SessionStateType.CLOSING)
                }
            }

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

    private fun closeSessionsAlreadyInClosing(checkpoint: FlowCheckpoint, sessions: List<String>) {
        val statesAlreadyInClosing = flowSessionManager.getSessionsWithStatus(checkpoint, sessions, SessionStateType.CLOSING)
        val sessionsAlreadyInClosing = statesAlreadyInClosing.map { it.sessionId }
        flowSessionManager.updateStatus(checkpoint, sessionsAlreadyInClosing, SessionStateType.CLOSED)
    }


    private fun filterOutErrorAndClosed(checkpoint: FlowCheckpoint, sessions: List<String>): List<String> {
        val statusToFilterOut = setOf(SessionStateType.ERROR, SessionStateType.CLOSED)
        val statesInErrorOrClosed = flowSessionManager.getSessionsWithStatuses(checkpoint, sessions, statusToFilterOut)
        val sessionsInErrorOrClosed = statesInErrorOrClosed.map { it.sessionId }
        return sessions - sessionsInErrorOrClosed
    }

    private fun getSessionsToCloseForWaitingFor(
        checkpoint: FlowCheckpoint,
        request: FlowIORequest.CloseSessions
    ): List<String> {
        val sessions = getSessionsToClose(checkpoint, request)

        //filter out initiated
        val initiatingAndInitiated = flowSessionManager.getInitiatingAndInitiatedSessions(sessions)
        val initiatingSessions = initiatingAndInitiated.first

        //filter out states
        val filteredSessions = filterOutErrorAndClosed(checkpoint, initiatingSessions)

        //filter out RequireClose false
        val requireCloseSessions = flowSessionManager.getRequireCloseTrueAndFalse(checkpoint, filteredSessions)

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
