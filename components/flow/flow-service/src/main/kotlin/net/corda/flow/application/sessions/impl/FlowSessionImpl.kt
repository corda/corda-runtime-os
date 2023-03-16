package net.corda.flow.application.sessions.impl

import net.corda.flow.application.serialization.DeserializedWrongAMQPObjectException
import net.corda.flow.application.serialization.SerializationServiceInternal
import net.corda.flow.application.sessions.FlowSessionInternal
import net.corda.flow.application.sessions.SessionInfo
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.state.FlowContext
import net.corda.utilities.debug
import net.corda.utilities.trace
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.LoggerFactory

@Suppress("LongParameterList", "TooManyFunctions")
class FlowSessionImpl(
    private val counterparty: MemberX500Name,
    private val sourceSessionId: String,
    private val flowFiberService: FlowFiberService,
    private val serializationService: SerializationServiceInternal,
    private val flowContext: FlowContext,
    direction: Direction
) : FlowSession, FlowSessionInternal {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun getCounterparty(): MemberX500Name = counterparty

    override fun getContextProperties(): FlowContextProperties = flowContext

    enum class Direction {
        INITIATING_SIDE,
        INITIATED_SIDE
    }

    private var isSessionConfirmed = when (direction) {
        Direction.INITIATING_SIDE -> false // Initiating flows need to establish a session
        Direction.INITIATED_SIDE -> true // Initiated flows are always instantiated as the result of an existing session
    }

    private val fiber: FlowFiber get() = flowFiberService.getExecutingFiber()

    override fun setSessionConfirmed() {
        isSessionConfirmed = true
    }

    override fun getSessionId(): String {
        return sourceSessionId
    }

    @Suspendable
    override fun <R : Any> sendAndReceive(receiveType: Class<R>, payload: Any): R {
        requireBoxedType(receiveType)
        val request = FlowIORequest.SendAndReceive(mapOf(getSessionInfo() to serialize(payload)))
        val received = fiber.suspend(request)
        setSessionConfirmed()
        return deserializeReceivedPayload(received, receiveType)
    }

    @Suspendable
    override fun <R : Any> receive(receiveType: Class<R>): R {
        requireBoxedType(receiveType)
        val request = FlowIORequest.Receive(setOf(getSessionInfo()))
        val received = fiber.suspend(request)
        setSessionConfirmed()
        return deserializeReceivedPayload(received, receiveType)
    }

    @Suspendable
    override fun send(payload: Any) {
        val request =
            FlowIORequest.Send(mapOf(getSessionInfo() to serialize(payload)))
        fiber.suspend(request)
        setSessionConfirmed()
    }

    @Suspendable
    override fun close() {
        if (isSessionConfirmed) {
            fiber.suspend(FlowIORequest.CloseSessions(setOf(sourceSessionId)))
            log.trace { "Closed session: $sourceSessionId" }
        } else {
            log.debug { "Ignoring close on uninitiated session: $sourceSessionId" }
        }
    }

    /**
     * Required to prevent class cast exceptions during AMQP serialization of primitive types.
     */
    private fun requireBoxedType(type: Class<*>) {
        require(!type.isPrimitive) { "Cannot receive primitive type $type" }
    }

    private fun serialize(payload: Any): ByteArray {
        return serializationService.serialize(payload).bytes
    }

    private fun <R : Any> deserializeReceivedPayload(
        received: Map<String, ByteArray>,
        receiveType: Class<R>
    ): R {
        return received[sourceSessionId]?.let {
            try {
                serializationService.deserializeAndCheckType(it, receiveType)
            } catch (e: DeserializedWrongAMQPObjectException) {
                throw CordaRuntimeException(
                    "Expecting to receive a ${e.expectedType} but received a ${e.deserializedType} instead, payload: " +
                            "(${e.deserializedObject})"
                )
            }
        }
            ?: throw CordaRuntimeException("The session [${sourceSessionId}] did not receive a payload when trying to receive one")
    }

    override fun getSessionInfo(): SessionInfo {
        return SessionInfo(
            sourceSessionId,
            counterparty,
            contextUserProperties = flowContext.flattenUserProperties(),
            contextPlatformProperties = flowContext.flattenPlatformProperties()
        )
    }

    override fun equals(other: Any?): Boolean =
        other === this || other is FlowSessionImpl && other.sourceSessionId == sourceSessionId

    override fun hashCode(): Int = sourceSessionId.hashCode()

    override fun toString(): String =
        "FlowSessionImpl(counterparty=$counterparty, sourceSessionId=$sourceSessionId, initiated=$isSessionConfirmed)"
}
