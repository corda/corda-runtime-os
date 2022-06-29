package net.cordapp.testing.chatframework

import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.UntrustworthyData
import net.corda.v5.base.types.MemberX500Name
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Sets up a mock FlowSession to return a counterparty name.
 */
fun FlowSession.withCounterpartyName(name: String): FlowSession {
    whenever(this.counterparty).thenReturn(MemberX500Name.parse(name))
    return this
}

/**
 * Sets up a mock FlowSession so that calls to receive() will return the specified payload.
 */
inline fun <reified T : Any> FlowSession.willReceive(payload: T): FlowSession {
    whenever(this.receive(T::class.java)).thenReturn(UntrustworthyData(payload))
    return this
}

/**
 * Verifies a payload was sent via this FlowSession.
 */
fun FlowSession.verifyMessageSent(payload: Any) {
    verify(this).send(payload)
}
