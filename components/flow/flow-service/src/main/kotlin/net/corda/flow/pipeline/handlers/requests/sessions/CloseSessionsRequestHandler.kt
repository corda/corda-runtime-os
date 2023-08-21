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
            getSessionsToClose(context.checkpoint, request, initiatingIdentity = true)
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
            val sessionsToCloseForInitiating = getSessionsToClose(checkpoint, request, initiatingIdentity = true)
            val sessionsToCloseForInitiated = getSessionsToClose(checkpoint, request, initiatingIdentity = false)

            // if I am the initiated party i.e ends with INITIATED_SESSION_ID_SUFFIX
            if(sessionsToCloseForInitiated.isNotEmpty()) {
                checkpoint.putSessionStates(flowSessionManager.sendCloseMessages(checkpoint, sessionsToCloseForInitiated, Instant.now()))
            } else if (sessionsToCloseForInitiating.isNotEmpty()) {

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

    private fun filterOutStatuses(
        checkpoint: FlowCheckpoint,
        request: FlowIORequest.CloseSessions,
        statusesToFilterOut: Set<SessionStateType>
    ): List<String> {
        val sessions = request.sessions.toList()
        val sessionsToFilterOut = flowSessionManager.getSessionsWithStatuses(checkpoint, sessions, statusesToFilterOut)
        sessions - sessionsToFilterOut.map { it.sessionId }
        return sessions
    }

    private fun filterByRequireClose(
        checkpoint: FlowCheckpoint,
        request: FlowIORequest.CloseSessions
    ): List<String> {
        val sessions = request.sessions.toList()
        val sessionsToFilterOut = flowSessionManager.getSessionsByRequireClose(checkpoint, sessions)
        sessions - sessionsToFilterOut.map { it.sessionId }
        return sessions
    }

    private fun filterByIdentity(
        checkpoint: FlowCheckpoint,
        request: FlowIORequest.CloseSessions
    ): List<String> {
        val sessions = request.sessions.toList()
        val sessionsToFilterOut = flowSessionManager.getSessionsWithRequireCloseTrue(checkpoint, sessions)
        sessions - sessionsToFilterOut.map { it.sessionId }
        return sessions
    }

    private fun getSessionsToClose(checkpoint: FlowCheckpoint, request: FlowIORequest.CloseSessions, initiatingIdentity: Boolean): List<String> {

        /**
         * else if i am initiating party i.e i dont end with INITIATED_SESSION_ID_SUFFIX
        if requireClose == false
        - dont return this session in the list
        else if requireClose == true
        - do return this session in the list

         && filter out sessions in status ERROR or CLOSED
         */

        val sessions = request.sessions.toList()
        val statusToFilterOut = setOf(SessionStateType.ERROR, SessionStateType.CLOSED)

        val sessionsInErrorOrClosed = flowSessionManager.getSessionsWithStatuses(checkpoint, sessions, statusToFilterOut)
        sessions - sessionsInErrorOrClosed.map { it.sessionId }

        val requiredCloseSessions = flowSessionManager.getSessionsByRequireClose(checkpoint, sessions, requireClose = true)
        sessions - requiredCloseSessions.map { it.sessionId }

        return if(initiatingIdentity) {
            sessions.filter { isInitiatingIdentity(it) }
        } else {
            sessions.filterNot { isInitiatingIdentity(it) }
        }

    }
}
