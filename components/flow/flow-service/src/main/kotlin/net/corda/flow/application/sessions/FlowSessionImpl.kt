package net.corda.flow.application.sessions

import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberSerializationService
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.v5.application.messaging.FlowInfo
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.UntrustworthyData
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger

class FlowSessionImpl(
    override val counterparty: MemberX500Name,
    private val sourceSessionId: String,
    private val flowFiberService: FlowFiberService,
    private val flowFiberSerializationService: FlowFiberSerializationService,
    private var initiated: Boolean
) : FlowSession {

    private companion object {
        val log = contextLogger()
    }

    private val fiber: FlowFiber get() = flowFiberService.getExecutingFiber()

    @Suspendable
    override fun getCounterpartyFlowInfo(): FlowInfo {
        TODO()
    }

    @Suspendable
    override fun <R : Any> sendAndReceive(receiveType: Class<R>, payload: Any): UntrustworthyData<R> {
        enforceNotPrimitive(receiveType)
        log.info("sessionId=${sourceSessionId} is init=${initiated}")
        ensureSessionIsOpen()
        log.info("sessionId=${sourceSessionId} is init=${initiated}")
        val request = FlowIORequest.SendAndReceive(mapOf(sourceSessionId to serialize(payload)))
        val received = fiber.suspend(request)
        return deserializeReceivedPayload(received, receiveType)
    }

    @Suspendable
    override fun <R : Any> receive(receiveType: Class<R>): UntrustworthyData<R> {
        enforceNotPrimitive(receiveType)
        ensureSessionIsOpen()
        val request = FlowIORequest.Receive(setOf(sourceSessionId))
        val received = fiber.suspend(request)
        return deserializeReceivedPayload(received, receiveType)
    }

    @Suspendable
    override fun send(payload: Any) {
        ensureSessionIsOpen()
        val request = FlowIORequest.Send(sessionToPayload = mapOf(sourceSessionId to serialize(payload)))
        return fiber.suspend(request)
    }

    @Suspendable
    override fun close() {
        if (initiated) {
            fiber.suspend(FlowIORequest.CloseSessions(setOf(sourceSessionId)))
            log.info("Closed session: $sourceSessionId")
        } else {
            log.info("Ignoring close on uninitiated session: $sourceSessionId")
        }
    }

    @Suspendable
    private fun ensureSessionIsOpen() {
        fun createInitiateFlowRequest(): FlowIORequest.InitiateFlow {
            // The creation of this message is pushed out to this nested builder method in order to ensure that when the
            // suspend method which receives it as an argument does a suspend that there is nothing on the stack to
            // accidentally serialize
            val flowContext = fiber.getExecutionContext().flowCheckpoint.flowContext
            return FlowIORequest.InitiateFlow(
                counterparty,
                sourceSessionId,
                contextUserProperties = flowContext.flattenUserProperties(),
                contextPlatformProperties = flowContext.flattenPlatformProperties()
            )
        }

        if (!initiated) {
            fiber.suspend(createInitiateFlowRequest())
            initiated = true
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
            UntrustworthyData(flowFiberSerializationService.deserializePayload(it, receiveType))
        } ?: throw CordaRuntimeException("The session [${sourceSessionId}] did not receive a payload when trying to receive one")
    }

    override fun equals(other: Any?): Boolean =
        other === this || other is FlowSessionImpl && other.sourceSessionId == sourceSessionId

    override fun hashCode(): Int = sourceSessionId.hashCode()

    override fun toString(): String =
        "FlowSessionImpl(counterparty=$counterparty, sourceSessionId=$sourceSessionId, initiated=$initiated)"
}