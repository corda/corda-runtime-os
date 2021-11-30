package net.corda.httprpc.security

import net.corda.httprpc.durablestream.DurableStreamContext
import net.corda.v5.application.identity.CordaX500Name
import net.corda.v5.base.annotations.CordaSerializable
import java.security.Principal

/**
 * Models the information needed to trace an invocation in Corda.
 * Includes initiating actor, origin, trace information, and optional external trace information to
 * correlate clients' IDs.
 *
 */
sealed class InvocationContext {

    abstract val arguments: List<Any?>
    abstract val clientId: String?

    /**
     * Returns the [Principal] for a given [Actor].
     */
    abstract val principal: Principal

    abstract fun setArguments(arguments: List<Any?>): InvocationContext

    /**
     * Origin was an RPC call.
     *
     * @property actor Acting agent of the invocation, used to derive the security principal.
     */
    // Field `actor` needs to stay public for AMQP / JSON serialization to work.
    data class Rpc(
            val actor: Actor,
            override val arguments: List<Any?> = emptyList(),
            val durableStreamContext: DurableStreamContext? = null,
            override val clientId: String? = null
    ) : InvocationContext() {
        override val principal = Principal { actor.id.value }
        override fun setArguments(arguments: List<Any?>): InvocationContext = copy(arguments = arguments)
        fun setClientId(clientId: String) = copy(clientId = clientId)
    }

    /**
     * Origin was a message sent by a [Peer].
     */
    data class Peer(
        val party: CordaX500Name,
        override val arguments: List<Any?> = emptyList(),
        override val clientId: String? = null
    ) : InvocationContext() {
        override val principal = Principal { party.toString() }
        override fun setArguments(arguments: List<Any?>): InvocationContext = copy(arguments = arguments)
    }

    /**
     * Origin was a Corda Service.
     */
    data class Service(
        val serviceClassName: String,

        override val arguments: List<Any?> = emptyList(),
        override val clientId: String? = null
    ) : InvocationContext() {
        override val principal = Principal { serviceClassName }
        override fun setArguments(arguments: List<Any?>): InvocationContext = copy(arguments = arguments)
    }
}

/**
 * Models an initiator in Corda, can be a user, a service, etc.
 */
@CordaSerializable
data class Actor(val id: Id, val serviceId: AuthServiceId, val owningLegalIdentity: CordaX500Name) {

    companion object {
        fun service(serviceClassName: String, owningLegalIdentity: CordaX500Name): Actor = Actor(
            Id(serviceClassName),
            AuthServiceId("SERVICE"), owningLegalIdentity
        )
    }

    /**
     * Actor id.
     */
    @CordaSerializable
    data class Id(val value: String)
}