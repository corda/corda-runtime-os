package net.corda.v5.application.flows

import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable

/**
 * [ResponderFlow] is a [Flow] that is started by receiving a message from a peer.
 *
 * A [ResponderFlow] must be annotated with [InitiatedBy] to be invoked by a session message. If both
 * these requirements are met, then the flow will be invoked via [ResponderFlow.call] which takes a [FlowSession]. This
 * session is created by the platform and communicates with the party that initiated the session.
 *
 * Flows implementing this interface must have a no-arg constructor. The flow invocation will fail if this constructor
 * does not exist.
 *
 * @see InitiatedBy
 */
interface ResponderFlow : Flow {

    /**
     * The business logic for the flow should be written here.
     *
     * This is equivalent to the call method for a normal flow. This version is invoked when the flow is started via an
     * incoming session init event, via a counterparty calling [FlowMessaging.initiateFlow].
     *
     * @param session The session opened by the counterparty.
     */
    @Suspendable
    fun call(session: FlowSession)
}