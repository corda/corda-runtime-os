@file:JvmName("SessionUtils")
package net.corda.flow.application.sessions.utils

import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.fiber.FlowFiberService
import net.corda.internal.serialization.amqp.AMQPNotSerializableException
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException

fun verifySessionStatusNotErrorOrClose(sessionId: String, flowFiberService: FlowFiberService) {
    val status = flowFiberService.getExecutingFiber().getExecutionContext().flowCheckpoint.getSessionState(
        sessionId
    )?.status

    if (setOf(SessionStateType.CLOSED, SessionStateType.ERROR).contains(status)) {
        throw CordaRuntimeException("Session: $sessionId Status is ${status?.name ?: "NULL"}")
    }
}

@Suppress("ComplexCondition")
fun requireAMQPSerializable(clazz: Class<*>) {
    if (!clazz.isAnnotationPresent(CordaSerializable::class.java)
            && !clazz.isInterface
            && !clazz.isEnum
            && !Collection::class.java.isAssignableFrom(clazz)
            && !Map::class.java.isAssignableFrom(clazz)) {
        throw AMQPNotSerializableException(
            clazz,
            "Class \"${clazz.name}\" is not annotated with @CordaSerializable.")
    }
}
