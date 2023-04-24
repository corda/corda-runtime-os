package net.corda.flow.application.sessions.utils

import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.fiber.FlowFiberService
import net.corda.v5.base.exceptions.CordaRuntimeException

internal object SessionUtils {
    fun verifySessionStatusNotErrorOrClose(sessionId: String, flowFiberService: FlowFiberService) {
        val status = flowFiberService.getExecutingFiber().getExecutionContext().flowCheckpoint.getSessionState(
            sessionId
        )?.status

        if (setOf(SessionStateType.CLOSED, SessionStateType.ERROR).contains(status)) {
            throw CordaRuntimeException("Session: $sessionId Status is ${status?.name ?: "NULL"}")
        }
    }
}