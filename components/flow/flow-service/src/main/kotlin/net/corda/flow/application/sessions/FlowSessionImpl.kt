package net.corda.flow.application.sessions

import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.sandbox.FlowSandboxContextTypes
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.v5.application.messaging.FlowInfo
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.UntrustworthyData
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import java.io.NotSerializableException

class FlowSessionImpl(
    override val counterparty: MemberX500Name,
    private val sourceSessionId: String,
    private val flowFiberService: FlowFiberService,
    private var state: State
) : FlowSession {

    private companion object {
        val log = contextLogger()
    }

    enum class State {
        UNINITIATED, INITIATED, CLOSED
    }

    private val fiber: FlowFiber<*> get() = flowFiberService.getExecutingFiber()

    @Suspendable
    override fun getCounterpartyFlowInfo(): FlowInfo {
        TODO()
    }

    @Suspendable
    override fun <R : Any> sendAndReceive(receiveType: Class<R>, payload: Any): UntrustworthyData<R> {
        enforceNotPrimitive(receiveType)
        ensureSessionIsOpen()
        val request = FlowIORequest.SendAndReceive(mapOf(sourceSessionId to serialize(payload)))
        val received = fiber.suspend(request)
        // TODO CORE-4156 Re-add `checkPayloadIs` code in `FlowSessionImpl`
        return deserializeReceivedPayload(received, receiveType)
    }

    @Suspendable
    override fun <R : Any> receive(receiveType: Class<R>): UntrustworthyData<R> {
        enforceNotPrimitive(receiveType)
        ensureSessionIsOpen()
        val request = FlowIORequest.Receive(setOf(sourceSessionId))
        val received = fiber.suspend(request)
        // TODO CORE-4156 Re-add `checkPayloadIs` code in `FlowSessionImpl`
        return deserializeReceivedPayload(received, receiveType)
    }

    @Suspendable
    override fun send(payload: Any) {
        ensureSessionIsOpen()
        val request = FlowIORequest.Send(sessionToPayload = mapOf(sourceSessionId to serialize(payload)))
        return fiber.suspend(request)
    }

    // marking closed here does not prevent a session closed at the end of a subflow, from being returned to the parent flow and then
    // executed upon. `closed` will still be false and it will go into the handlers and break further down, that is probably fine if we
    // convey a good error message back to the flow later on.
    @Suspendable
    override fun close() {
        when (state) {
            State.UNINITIATED -> log.info("Ignoring close on uninitiated session: $sourceSessionId")
            State.INITIATED -> {
                fiber.suspend(FlowIORequest.CloseSessions(setOf(sourceSessionId)))
                val sessionIds = flowFiberService
                    .getExecutingFiber()
                    .getExecutionContext()
                    .flowStackService
                    .peek()
                    ?.sessionIds
                    ?.toMutableList()
                sessionIds?.remove(sourceSessionId)
                flowFiberService.getExecutingFiber().getExecutionContext().flowStackService.peek()?.sessionIds =
                    sessionIds
                state = State.CLOSED
                log.info("Closed session: $sourceSessionId")
            }
            State.CLOSED -> log.info("Ignoring duplicate close on session: $sourceSessionId")
        }
    }

    @Suspendable
    private fun ensureSessionIsOpen() {
        if (state == State.UNINITIATED) {
            flowFiberService.getExecutingFiber().suspend(FlowIORequest.InitiateFlow(counterparty, sourceSessionId))
            state = State.INITIATED
        } else if (state == State.CLOSED) throw CordaRuntimeException("Session: $sourceSessionId is closed")
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
                UntrustworthyData(deserialize(it, receiveType))
            } catch (e: NotSerializableException) {
                log.info("Received a payload but failed to deserialize it into a ${receiveType.name}", e)
                throw e
            }
        }
            ?: throw CordaRuntimeException("The session [${sourceSessionId}] did not receive a payload when trying to receive one")
    }

    private fun <R : Any> deserialize(payload: ByteArray, receiveType: Class<R>): R {
        return getSerializationService().deserialize(payload, receiveType)
    }

    private fun getSerializationService(): SerializationService {
        return fiber.getExecutionContext().run {
            sandboxGroupContext.getObjectByKey(FlowSandboxContextTypes.AMQP_P2P_SERIALIZATION_SERVICE)
                ?: throw CordaRuntimeException("P2P serialization service not found within the sandbox for identity: $holdingIdentity")
        }
    }

    override fun equals(other: Any?): Boolean =
        other === this || other is FlowSessionImpl && other.sourceSessionId == sourceSessionId

    override fun hashCode(): Int = sourceSessionId.hashCode()

    override fun toString(): String =
        "FlowSessionImpl(counterparty=$counterparty, sourceSessionId=$sourceSessionId, state=$state)"
}