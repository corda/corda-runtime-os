package net.corda.rest.security.read.rbac

import javax.security.auth.login.FailedLoginException
import net.corda.rest.security.AuthServiceId
import net.corda.rest.security.AuthorizingSubject
import net.corda.rest.security.read.Password
import net.corda.rest.security.read.RestSecurityManager
import net.corda.libs.permission.PermissionValidator
import net.corda.libs.permissions.manager.BasicAuthenticationService
import java.util.function.Supplier

class RBACSecurityManager(
    private val permissionValidatorSupplier: Supplier<PermissionValidator>,
    private val basicAuthenticationService: BasicAuthenticationService
) : RestSecurityManager {

    override val id = AuthServiceId(RBACSecurityManager::class.java.name)

    override fun authenticate(principal: String, password: Password): AuthorizingSubject {
        if(!basicAuthenticationService.authenticateUser(principal.lowercase(), password.value)) {
            throw FailedLoginException("User not authenticated.")
        }

        return buildSubject(principal)
    }

    override fun buildSubject(principal: String): AuthorizingSubject {
        return RBACAuthorizingSubject(permissionValidatorSupplier, principal)
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
