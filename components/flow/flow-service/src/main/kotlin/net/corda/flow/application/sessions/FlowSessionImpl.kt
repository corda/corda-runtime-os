package net.corda.flow.application.sessions

import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.state.impl.FlatSerializableContext
import net.corda.flow.state.impl.MutableFlatSerializableContext
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.messaging.FlowInfo
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.UntrustworthyData
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.castIfPossible
import net.corda.v5.base.util.contextLogger
import java.io.NotSerializableException

class FlowSessionImpl private constructor(
    override val counterparty: MemberX500Name,
    private val sourceSessionId: String,
    private val flowFiberService: FlowFiberService,
    direction: Direction
) : FlowSession {

    companion object {
        fun asInitiatingSession(
            counterparty: MemberX500Name,
            sourceSessionId: String,
            flowFiberService: FlowFiberService
        ): FlowSession =
            FlowSessionImpl(counterparty, sourceSessionId, flowFiberService, Direction.INITIATING_SIDE)

        fun asInitiatedSession(
            counterparty: MemberX500Name,
            sourceSessionId: String,
            flowFiberService: FlowFiberService,
            contextProperties: Map<String, String>
        ) = FlowSessionImpl(counterparty, sourceSessionId, flowFiberService, Direction.INITIATED_SIDE).apply {
            sessionLocalFlowContext = FlatSerializableContext(
                contextUserProperties = emptyMap(),
                contextPlatformProperties = contextProperties
            )
        }

        private val log = contextLogger()
    }

    internal enum class Direction {
        INITIATING_SIDE,
        INITIATED_SIDE
    }

    private var isSessionConfirmed = when (direction) {
        Direction.INITIATING_SIDE -> false // Initiating flows need to establish a session
        Direction.INITIATED_SIDE -> true // Initiated flows are always instantiated as the result of an existing session
    }

    private val fiber: FlowFiber get() = flowFiberService.getExecutingFiber()

    /**
     * This class can be serialized, so we need to ensure all properties support that too. In this case that means we
     * cannot touch the executing fiber when setting or getting this property, because we might not be in one. Instead
     * that is done when [contextProperties] is requested or [confirmSession] called, because both of those things can
     * only happen inside a fiber by contract.
     */
    internal var sessionLocalFlowContext: FlatSerializableContext? = null

    override val contextProperties: FlowContextProperties
        get() {
            initialiseSessionLocalFlowContext()
            return sessionLocalFlowContext!!
        }

    private fun initialiseSessionLocalFlowContext() {
        if (sessionLocalFlowContext == null) {
            with(fiber.getExecutionContext().flowCheckpoint.flowContext) {
                sessionLocalFlowContext = MutableFlatSerializableContext(
                    contextUserProperties = this.flattenUserProperties(),
                    contextPlatformProperties = this.flattenPlatformProperties()
                )
            }
        }
    }

    @Suspendable
    override fun getCounterpartyFlowInfo(): FlowInfo {
        TODO()
    }

    @Suspendable
    override fun <R : Any> sendAndReceive(receiveType: Class<R>, payload: Any): UntrustworthyData<R> {
        enforceNotPrimitive(receiveType)
        log.info("sessionId=${sourceSessionId} is init=${isSessionConfirmed}")
        confirmSession()
        log.info("sessionId=${sourceSessionId} is init=${isSessionConfirmed}")
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
            initialiseSessionLocalFlowContext()
            fiber.suspend(
                FlowIORequest.InitiateFlow(
                    counterparty,
                    sourceSessionId,
                    contextUserProperties = sessionLocalFlowContext!!.flattenUserProperties(),
                    contextPlatformProperties = sessionLocalFlowContext!!.flattenPlatformProperties()
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
        return getSerializationService().serialize(payload).bytes
    }

    private fun <R : Any> deserializeReceivedPayload(
        received: Map<String, ByteArray>,
        receiveType: Class<R>
    ): UntrustworthyData<R> {
        return received[sourceSessionId]?.let {
            try {
                val payload = getSerializationService().deserialize(it, receiveType)
                checkPayloadIs(payload, receiveType)
                UntrustworthyData(payload)
            } catch (e: NotSerializableException) {
                log.info("Received a payload but failed to deserialize it into a ${receiveType.name}", e)
                throw e
            }
        }
            ?: throw CordaRuntimeException("The session [${sourceSessionId}] did not receive a payload when trying to receive one")
    }

    /**
     * AMQP deserialization outputs an object whose type is solely based on the serialized content, therefore although the generic type is
     * specified, it can still be the wrong type. We check this type here, so that we can throw an accurate error instead of failing later
     * on when the object is used.
     */
    private fun <R : Any> checkPayloadIs(payload: Any, receiveType: Class<R>) {
        receiveType.castIfPossible(payload) ?: throw CordaRuntimeException(
            "Expecting to receive a ${receiveType.name} but received a ${payload.javaClass.name} instead, payload: ($payload)"
        )
    }

    private fun getSerializationService(): SerializationService {
        return fiber.getExecutionContext().run {
            sandboxGroupContext.amqpSerializer
        }
    }

    override fun equals(other: Any?): Boolean =
        other === this || other is FlowSessionImpl && other.sourceSessionId == sourceSessionId

    override fun hashCode(): Int = sourceSessionId.hashCode()

    override fun toString(): String =
        "FlowSessionImpl(counterparty=$counterparty, sourceSessionId=$sourceSessionId, initiated=$isSessionConfirmed)"
}