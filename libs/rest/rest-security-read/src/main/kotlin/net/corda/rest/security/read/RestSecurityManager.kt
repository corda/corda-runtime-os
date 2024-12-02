package net.corda.rest.security.read

import net.corda.data.rest.PasswordExpiryStatus
import net.corda.lifecycle.Lifecycle
import net.corda.rest.authorization.AuthorizingSubject
import net.corda.rest.security.AuthServiceId
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
    fun buildSubject(principal: String, expiryStatus: PasswordExpiryStatus): AuthorizingSubject
}
