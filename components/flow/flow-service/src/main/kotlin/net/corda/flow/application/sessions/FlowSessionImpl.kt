package net.corda.flow.application.sessions

import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.v5.application.flows.FlowInfo
import net.corda.v5.application.flows.FlowSession
import net.corda.v5.application.flows.UntrustworthyData
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.uncheckedCast

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
        val request = FlowIORequest.SendAndReceive(mapOf(sourceSessionId to payload))
        val received = fiber.suspend(request)
        // TODO CORE-4156 Re-add `checkPayloadIs` code in `FlowSessionImpl`
        return UntrustworthyData(uncheckedCast(received[sourceSessionId]))
    }

    @Suspendable
    override fun <R : Any> receive(receiveType: Class<R>): UntrustworthyData<R> {
        enforceNotPrimitive(receiveType)
        ensureSessionIsOpen()
        val request = FlowIORequest.Receive(setOf(sourceSessionId))
        val received = fiber.suspend(request)
        // TODO CORE-4156 Re-add `checkPayloadIs` code in `FlowSessionImpl`
        return UntrustworthyData(uncheckedCast(received[sourceSessionId]))
    }

    @Suspendable
    override fun send(payload: Any) {
        ensureSessionIsOpen()
        return fiber.suspend(FlowIORequest.Send(sessionToMessage = mapOf(sourceSessionId to payload)))
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
                flowFiberService.getExecutingFiber().getExecutionContext().flowStackService.peek()?.sessionIds = sessionIds
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
        }
        else if (state == State.CLOSED) throw CordaRuntimeException("Session: $sourceSessionId is closed")
    }

    /**
     * Required to prevent class cast exceptions during AMQP serialization of primitive types.
     */
    private fun enforceNotPrimitive(type: Class<*>) {
        require(!type.isPrimitive) { "Cannot receive primitive type $type" }
    }

    override fun equals(other: Any?): Boolean = other === this || other is FlowSessionImpl && other.sourceSessionId == sourceSessionId

    override fun hashCode(): Int = sourceSessionId.hashCode()

    override fun toString(): String = "FlowSessionImpl(counterparty=$counterparty, sourceSessionId=$sourceSessionId, state=$state)"
}