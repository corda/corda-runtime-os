package net.corda.flow.manager.impl

import co.paralleluniverse.fibers.Fiber
import net.corda.flow.manager.FlowIORequest
import net.corda.flow.manager.FlowSession
import net.corda.flow.manager.FlowStateMachine
import net.corda.v5.application.flows.Destination
import net.corda.v5.application.flows.FlowInfo
import net.corda.v5.application.flows.UntrustworthyData
import net.corda.v5.application.identity.Party
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.context.Trace
import net.corda.v5.base.internal.castIfPossible
import net.corda.v5.base.types.NonEmptySet
import net.corda.v5.base.util.contextLogger
import net.corda.v5.serialization.SerializedBytes

class FlowSessionImpl(
    override val destination: Destination,
    private val wellKnownParty: Party,
    val sourceSessionId: Trace.SessionId,
    var sequenceNo: Int = 0
) : FlowSession() {
    companion object {
        val log = contextLogger()
    }

    override val counterparty: Party get() = wellKnownParty

    override fun toString(): String = "FlowSessionImpl(destination=$destination, sourceSessionId=$sourceSessionId)"

    override fun equals(other: Any?): Boolean =
        other === this || other is FlowSessionImpl && other.sourceSessionId == sourceSessionId

    override fun hashCode(): Int = sourceSessionId.hashCode()

    private val flowStateMachine: FlowStateMachine<*> get() = Fiber.currentFiber() as FlowStateMachine<*>

    @Suspendable
    override fun getCounterpartyFlowInfo(maySkipCheckpoint: Boolean): FlowInfo {
        val request = FlowIORequest.GetFlowInfo(NonEmptySet.of(this))
        return flowStateMachine.suspend(request, maySkipCheckpoint).getValue(this)
    }

    @Suspendable
    override fun getCounterpartyFlowInfo(): FlowInfo = getCounterpartyFlowInfo(maySkipCheckpoint = false)

    @Suspendable
    override fun <R : Any> sendAndReceive(
        receiveType: Class<R>,
        payload: Any,
        maySkipCheckpoint: Boolean
    ): UntrustworthyData<R> {
        log.info("sendAndReceive entered")
        enforceNotPrimitive(receiveType)
        val request = FlowIORequest.SendAndReceive(
            sessionToMessage = mapOf(this to flowStateMachine.serializationService.serialize(payload)),
            shouldRetrySend = false
        )
        log.info("sendAndReceive preSuspend")
        val responseValues: Map<FlowSession, SerializedBytes<Any>> =
            flowStateMachine.suspend(request, maySkipCheckpoint)
        val responseForCurrentSession = responseValues.getValue(this)
        log.info("sendAndReceive postSuspend")

        val payloadData =
            flowStateMachine.serializationService.deserialize(responseForCurrentSession.bytes, receiveType)
        val cast = receiveType.castIfPossible(payloadData)
            ?: throw IllegalArgumentException("We were expecting a ${receiveType.name} but we instead got a " +
                    "${payloadData.javaClass.name} ($payloadData)")
        return UntrustworthyData(cast)
    }

    @Suspendable
    override fun <R : Any> sendAndReceive(receiveType: Class<R>, payload: Any) =
        sendAndReceive(receiveType, payload, maySkipCheckpoint = false)

    @Suspendable
    override fun <R : Any> receive(receiveType: Class<R>, maySkipCheckpoint: Boolean): UntrustworthyData<R> {
        enforceNotPrimitive(receiveType)
        val request = FlowIORequest.Receive(NonEmptySet.of(this))
        return flowStateMachine.suspend(request, maySkipCheckpoint).getValue(this).let {
            val payloadData = flowStateMachine.serializationService.deserialize(it.bytes, receiveType)
            val cast = receiveType.castIfPossible(payloadData)
                ?: throw IllegalArgumentException("We were expecting a ${receiveType.name} but we instead got a " +
                        "${payloadData.javaClass.name} ($payloadData)")
            UntrustworthyData(cast)
        }
    }

    @Suspendable
    override fun <R : Any> receive(receiveType: Class<R>) = receive(receiveType, maySkipCheckpoint = false)

    @Suspendable
    override fun send(payload: Any, maySkipCheckpoint: Boolean) {
        val request = FlowIORequest.Send(
            sessionToMessage = mapOf(this to flowStateMachine.serializationService.serialize(payload))
        )
        return flowStateMachine.suspend(request, maySkipCheckpoint)
    }

    @Suspendable
    override fun send(payload: Any) = send(payload, maySkipCheckpoint = false)

    @Suspendable
    override fun close() {
        val request = FlowIORequest.CloseSessions(NonEmptySet.of(this))
        return flowStateMachine.suspend(request, false)
    }

    private fun enforceNotPrimitive(type: Class<*>) {
        require(!type.isPrimitive) { "Cannot receive primitive type $type" }
    }
}
