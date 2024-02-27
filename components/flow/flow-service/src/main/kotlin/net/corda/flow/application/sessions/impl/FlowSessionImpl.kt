package net.corda.flow.application.sessions.impl

import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.application.serialization.DeserializedWrongAMQPObjectException
import net.corda.flow.application.serialization.FlowSerializationService
import net.corda.flow.application.sessions.FlowSessionInternal
import net.corda.flow.application.sessions.SessionInfo
import net.corda.flow.application.sessions.utils.SessionUtils.verifySessionStatusNotErrorOrClose
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.state.FlowContext
import net.corda.flow.utils.KeyValueStore
import net.corda.session.manager.Constants
import net.corda.utilities.trace
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.messaging.FlowInfo
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.LoggerFactory
import java.time.Duration

@Suppress("LongParameterList", "TooManyFunctions")
class FlowSessionImpl(
    private val counterparty: MemberX500Name,
    private val sourceSessionId: String,
    private val flowFiberService: FlowFiberService,
    private val serializationService: FlowSerializationService,
    private val flowContext: FlowContext,
    direction: Direction,
    private val requireClose: Boolean,
    private val sessionTimeout: Duration? = null,
) : FlowSession, FlowSessionInternal {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun getCounterparty(): MemberX500Name = counterparty

    @Suspendable
    override fun getCounterpartyFlowInfo(): FlowInfo {
        val counterPartyFlowInfo = getFlowInfoFromSessionContext()
        return if (counterPartyFlowInfo != null) {
            counterPartyFlowInfo
        } else {
            val request = FlowIORequest.CounterPartyFlowInfo(getSessionInfo())
            fiber.suspend(request)
            //If we are able to receive counterparty info this means the session initiation has been completed.
            setSessionConfirmed()
            getFlowInfoFromSessionContext() ?: throw CordaRuntimeException(
                "Failed to get counterparties flow info. Session is in an " +
                        "invalid state"
            )
        }
    }

    private fun getFlowInfoFromSessionContext(): FlowInfo? {
        val sessionState = flowFiberService.getExecutingFiber().getExecutionContext().flowCheckpoint.getSessionState(sourceSessionId)
        val sessionProperties = sessionState?.sessionProperties ?: return null
        val props = KeyValueStore(sessionProperties)
        val protocol = props[Constants.FLOW_PROTOCOL]
        val protocolVersion = props[Constants.FLOW_PROTOCOL_VERSION_USED]?.toInt()
        if (protocol == null || protocolVersion == null) return null
        return FlowInfoImpl(protocol, protocolVersion)
    }

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
        verifySessionStatusNotErrorOrClose(sourceSessionId, flowFiberService)
        val request = FlowIORequest.SendAndReceive(mapOf(getSessionInfo() to serialize(payload)))
        val received = fiber.suspend(request)

        setSessionConfirmed()

        return processReceivedPayload(received, receiveType)
    }

    @Suspendable
    override fun <R : Any> receive(receiveType: Class<R>): R {
        verifySessionStatusNotErrorOrClose(sourceSessionId, flowFiberService)
        val request = FlowIORequest.Receive(setOf(getSessionInfo()))
        val received = fiber.suspend(request)
        setSessionConfirmed()
        return processReceivedPayload(received, receiveType)
    }

    @Suspendable
    override fun send(payload: Any) {
        verifySessionStatusNotErrorOrClose(sourceSessionId, flowFiberService)
        val request = FlowIORequest.Send(mapOf(getSessionInfo() to serialize(payload)))
        fiber.suspend(request)
    }

    @Suspendable
    override fun close() {
        if (canCloseSession()) {
            fiber.suspend(FlowIORequest.CloseSessions(setOf(sourceSessionId)))
            log.trace { "Closing session: $sourceSessionId" }
        }
    }

    private fun canCloseSession() : Boolean {
        val flowCheckpoint = flowFiberService.getExecutingFiber().getExecutionContext().flowCheckpoint
        val sessionState = flowCheckpoint.getSessionState(sourceSessionId)
        return sessionState?.status != SessionStateType.CLOSED
    }

    private fun serialize(payload: Any): ByteArray {
        return serializationService.serialize(payload).bytes
    }

    private fun <R : Any> processReceivedPayload(received: Map<String, ByteArray>, receiveType: Class<R>): R {
        return if (receiveType.isPrimitive) {
            deserializeReceivedPayload(received, receiveType.kotlin.javaObjectType)
        } else {
            deserializeReceivedPayload(received, receiveType)
        }
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
            requireClose,
            sessionTimeout,
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
