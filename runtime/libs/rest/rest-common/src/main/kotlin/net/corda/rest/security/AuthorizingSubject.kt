package net.corda.rest.security

/**
 * Provides permission checking for the subject identified by the given [principal].
 */
interface AuthorizingSubject {

    /**
     * Identity of underlying subject
     */
    val principal: String

    /**
     * Determines if the underlying subject is entitled to perform a certain action,
     * (e.g. an RPC invocation) represented by an [action] string followed by an
     * optional list of arguments.
     */
    fun isPermitted(action: String, vararg arguments: String): Boolean
}