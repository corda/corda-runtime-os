package net.corda.flow.application.sessions.utils

import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.pipeline.exceptions.FlowPlatformException
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

    fun checkPayloadMaxSize(serializedPayload: ByteArray, flowFiberService: FlowFiberService) {
        val payloadSize = serializedPayload.size
        val checkpoint = flowFiberService.getExecutingFiber().getExecutionContext().flowCheckpoint
        val maxPayloadSize = checkpoint.maxPayloadSize

        if (payloadSize > maxPayloadSize) {
            throw FlowPlatformException("Payload size: $payloadSize bytes larger than allowed: $maxPayloadSize bytes.")
        }
    }
}