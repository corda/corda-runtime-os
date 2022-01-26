package net.corda.httprpc.security.read.rbac

import net.corda.httprpc.security.AuthServiceId
import net.corda.httprpc.security.AuthorizingSubject
import net.corda.httprpc.security.read.Password
import net.corda.httprpc.security.read.RPCSecurityException
import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.libs.permission.PermissionValidator

class RBACSecurityManager(
    private val permissionValidator: PermissionValidator
) : RPCSecurityManager {

    override val id = AuthServiceId(RBACSecurityManager::class.java.name)

    override fun authenticate(principal: String, password: Password): AuthorizingSubject {

        if(!permissionValidator.authenticateUser(principal, password.value)) {
            throw RPCSecurityException("User not authenticated.")
        }

        return buildSubject(principal)
    }

    override fun buildSubject(principal: String): AuthorizingSubject {
        return RBACAuthorizingSubject(permissionValidator, principal)
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