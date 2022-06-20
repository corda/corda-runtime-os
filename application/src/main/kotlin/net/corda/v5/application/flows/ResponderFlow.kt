package net.corda.v5.application.flows

import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable

/**
 * A flow that may be started by receiving a message from a peer.
 *
 * A flow implementing this will also need to be marked as @InitiatedBy to be invoked via a session message. If both
 * these requirements are met, then the flow will be invoked via the `call` method that takes a session. This session is
 * created by the platform and communicates with the party that initiated the session.
 *
 * Note that an initiated flow cannot return a value.
 *
 * Flows implementing this interface must have a no-arg constructor. The flow invocation will fail if this constructor
 * does not exist.
 */
interface ResponderFlow : Flow {

    /**
     * The business logic for the flow should be written here.
     *
     * This is equivalent to the call method for a normal flow. This version is invoked when the flow is started via an
     * incoming session init event, via a counterparty calling `initiateFlow`.
     *
     * @param session The session opened by the counterparty.
     */
    @Suspendable
    fun call(session: FlowSession)
}