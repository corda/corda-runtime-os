package net.corda.httprpc.security

import net.corda.httprpc.durablestream.DurableStreamContext
import net.corda.v5.base.types.MemberX500Name
import java.security.Principal

/**
 * Models the information needed to trace a Rest invocation in Corda.
 * Includes initiating actor, origin, trace information, and optional external trace information to correlate clients' IDs.
 *
 * @property actor Acting agent of the invocation, used to derive the security principal.
 */
data class InvocationContext(
    val actor: Actor,
    val arguments: List<Any?> = emptyList(),
    val durableStreamContext: DurableStreamContext? = null,
    val clientId: String? = null
) {
    /**
     * Returns the [Principal] for a given [Actor].
     */
    val principal: Principal = Principal { actor.id.value }
}

/**
 * Models an initiator in Corda, can be a user, a service, etc.
 */
data class Actor(val id: Id, val serviceId: AuthServiceId, val owningLegalIdentity: MemberX500Name) {

    companion object {
        fun service(serviceClassName: String, owningLegalIdentity: MemberX500Name): Actor = Actor(
            Id(serviceClassName),
            AuthServiceId("SERVICE"), owningLegalIdentity
        )
    }

    /**
     * Actor id.
     */
    data class Id(val value: String)
}