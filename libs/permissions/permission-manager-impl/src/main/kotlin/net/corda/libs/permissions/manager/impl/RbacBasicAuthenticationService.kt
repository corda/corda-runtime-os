package net.corda.libs.permissions.manager.impl

import net.corda.libs.permissions.management.cache.PermissionManagementCache
import net.corda.libs.permissions.manager.BasicAuthenticationService
import net.corda.permissions.password.PasswordHash
import net.corda.permissions.password.PasswordService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug

class RbacBasicAuthenticationService(
    private val permissionManagementCache: PermissionManagementCache,
    private val passwordService: PasswordService
) : BasicAuthenticationService {

    companion object {
        private val logger = contextLogger()
    }

    override fun authenticateUser(loginName: String, password: CharArray): Boolean {
        logger.debug { "Checking authentication for user $loginName." }
        val user = permissionManagementCache.getUser(loginName) ?: return false

        if (user.saltValue == null || user.hashedPassword == null) {
            return false
        }

        if (!passwordService.verifies(String(password), PasswordHash(user.saltValue, user.hashedPassword))) {
            return false
        }

        return true
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