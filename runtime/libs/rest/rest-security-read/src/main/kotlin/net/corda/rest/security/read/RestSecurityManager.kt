package net.corda.rest.security.read

import net.corda.rest.security.AuthServiceId
import net.corda.rest.security.AuthorizingSubject
import net.corda.lifecycle.Lifecycle
import javax.security.auth.login.FailedLoginException

/**
 * Manage security of Rest users, providing logic for user authentication and authorization.
 */
interface RestSecurityManager : Lifecycle {
    /**
     * An identifier associated to this security service
     */
    val id: AuthServiceId

    /**
     * Perform user authentication from principal and password. Return an [AuthorizingSubject] containing
     * the permissions of the user identified by the given [principal] if authentication via password succeeds,
     * otherwise a [FailedLoginException] is thrown.
     */
    fun authenticate(principal: String, password: Password): AuthorizingSubject

    /**
     * Construct an [AuthorizingSubject] instance con permissions of the user associated to
     * the given principal. Throws an exception if the principal cannot be resolved to a known user.
     */
    fun buildSubject(principal: String): AuthorizingSubject
}


/**
 * Non-throwing version of authenticate, returning null instead of throwing in case of authentication failure
 */
fun RestSecurityManager.tryAuthenticate(principal: String, password: Password): AuthorizingSubject? {
    password.use {
        return try {
            authenticate(principal, password)
        } catch (e: FailedLoginException) {
            null
        }
    }
}
