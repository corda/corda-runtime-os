package net.corda.flow.application.sessions

import net.corda.flow.fiber.DeserializedWrongAMQPObjectException
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberSerializationService
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.state.FlowContext
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.messaging.FlowInfo
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.UntrustworthyData
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger

@Suppress("LongParameterList")
class FlowSessionImpl(
    override val counterparty: MemberX500Name,
    private val sourceSessionId: String,
    private val flowFiberService: FlowFiberService,
    private val flowFiberSerializationService: FlowFiberSerializationService,
    private val flowContext: FlowContext,
    direction: Direction
) : FlowSession {

    private companion object {
        private val log = contextLogger()
    }

    override val contextProperties: FlowContextProperties = flowContext

    enum class Direction {
        INITIATING_SIDE,
        INITIATED_SIDE
    }

    private var isSessionConfirmed = when (direction) {
        Direction.INITIATING_SIDE -> false // Initiating flows need to establish a session
        Direction.INITIATED_SIDE -> true // Initiated flows are always instantiated as the result of an existing session
    }

    private val fiber: FlowFiber get() = flowFiberService.getExecutingFiber()

    @Suspendable
    override fun getCounterpartyFlowInfo(): FlowInfo {
        TODO()
    }

    @Suspendable
    override fun <R : Any> sendAndReceive(receiveType: Class<R>, payload: Any): UntrustworthyData<R> {
        enforceNotPrimitive(receiveType)
        confirmSession()
        val request = FlowIORequest.SendAndReceive(mapOf(sourceSessionId to serialize(payload)))
        val received = fiber.suspend(request)
        return deserializeReceivedPayload(received, receiveType)
    }

    @Suspendable
    override fun <R : Any> receive(receiveType: Class<R>): UntrustworthyData<R> {
        enforceNotPrimitive(receiveType)
        confirmSession()
        val request = FlowIORequest.Receive(setOf(sourceSessionId))
        val received = fiber.suspend(request)
        return deserializeReceivedPayload(received, receiveType)
    }

    @Suspendable
    override fun send(payload: Any) {
        confirmSession()
        val request = FlowIORequest.Send(sessionToPayload = mapOf(sourceSessionId to serialize(payload)))
        return fiber.suspend(request)
    }

    @Suspendable
    override fun close() {
        if (isSessionConfirmed) {
            fiber.suspend(FlowIORequest.CloseSessions(setOf(sourceSessionId)))
            log.info("Closed session: $sourceSessionId")
        } else {
            log.info("Ignoring close on uninitiated session: $sourceSessionId")
        }
    }

    @Suspendable
    private fun confirmSession() {
        if (!isSessionConfirmed) {
            fiber.suspend(
                FlowIORequest.InitiateFlow(
                    counterparty,
                    sourceSessionId,
                    contextUserProperties = flowContext.flattenUserProperties(),
                    contextPlatformProperties = flowContext.flattenPlatformProperties()
                )
            )
            isSessionConfirmed = true
        }
    }

    /**
     * Required to prevent class cast exceptions during AMQP serialization of primitive types.
     */
    private fun enforceNotPrimitive(type: Class<*>) {
        require(!type.isPrimitive) { "Cannot receive primitive type $type" }
    }

    private fun serialize(payload: Any): ByteArray {
        return flowFiberSerializationService.serialize(payload).bytes
    }

    private fun <R : Any> deserializeReceivedPayload(
        received: Map<String, ByteArray>,
        receiveType: Class<R>
    ): UntrustworthyData<R> {
        return received[sourceSessionId]?.let {
            try {
                UntrustworthyData(flowFiberSerializationService.deserialize(it, receiveType))
            } catch (e: DeserializedWrongAMQPObjectException) {
                throw CordaRuntimeException(
                    "Expecting to receive a ${e.expectedType} but received a ${e.deserializedType} instead, payload: " +
                            "(${e.deserializedObject})"
                )
            }
        }
            ?: throw CordaRuntimeException("The session [${sourceSessionId}] did not receive a payload when trying to receive one")
    }

    override fun equals(other: Any?): Boolean =
        other === this || other is FlowSessionImpl && other.sourceSessionId == sourceSessionId

    override fun hashCode(): Int = sourceSessionId.hashCode()

    override fun toString(): String =
        "FlowSessionImpl(counterparty=$counterparty, sourceSessionId=$sourceSessionId, initiated=$isSessionConfirmed)"
}