package net.corda.rest.security.read.rbac

import net.corda.data.rest.PasswordExpiryStatus
import net.corda.libs.permission.PermissionValidator
import net.corda.libs.permissions.manager.BasicAuthenticationService
import net.corda.rest.authorization.AuthorizingSubject
import net.corda.rest.security.AuthServiceId
import net.corda.rest.security.read.Password
import net.corda.rest.security.read.RestSecurityManager
import java.util.function.Supplier
import javax.security.auth.login.FailedLoginException

class RBACSecurityManager(
    private val permissionValidatorSupplier: Supplier<PermissionValidator>,
    private val basicAuthenticationService: BasicAuthenticationService
) : RestSecurityManager {

    override val id = AuthServiceId(RBACSecurityManager::class.java.name)

    override fun authenticate(principal: String, password: Password): AuthorizingSubject {
        val authenticationState = basicAuthenticationService.authenticateUser(principal.lowercase(), password.value)
        if (!authenticationState.authenticationSuccess) {
            throw FailedLoginException("User not authenticated.")
        }

        return buildSubject(principal, authenticationState.expiryStatus)
    }

    override fun buildSubject(principal: String, expiryStatus: PasswordExpiryStatus?): AuthorizingSubject {
        return RBACAuthorizingSubject(permissionValidatorSupplier, principal, expiryStatus)
    }

    private var running = false

    override val isRunning: Boolean
        get() = running

    override fun start() {
        running = true
    }

    override fun stop() {
        running = false
    }
}
