package net.corda.libs.permissions.manager.impl

import net.corda.libs.permissions.management.cache.PermissionManagementCache
import net.corda.libs.permissions.manager.BasicAuthenticationService
import net.corda.permissions.password.PasswordHash
import net.corda.permissions.password.PasswordService
import net.corda.v5.base.util.debug
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

class RbacBasicAuthenticationService(
    private val permissionManagementCacheRef: AtomicReference<PermissionManagementCache?>,
    private val passwordService: PasswordService
) : BasicAuthenticationService {

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val repeatedLogonsCache = RepeatedLogonsCache()

    override fun authenticateUser(loginName: String, password: CharArray): Boolean {
        logger.debug { "Checking authentication for user $loginName." }
        val permissionManagementCache = checkNotNull(permissionManagementCacheRef.get()) {
            "Permission management cache is null."
        }

        val clearTextPassword = String(password)

        if(repeatedLogonsCache.verifies(loginName, clearTextPassword)) {
            return true
        }

        val user = permissionManagementCache.getUser(loginName) ?: return false

        if (user.saltValue == null || user.hashedPassword == null) {
            return false
        }

        if (passwordService.verifies(clearTextPassword, PasswordHash(user.saltValue, user.hashedPassword))) {
            repeatedLogonsCache.add(loginName, clearTextPassword)
            return true
        }

        repeatedLogonsCache.remove(loginName)
        return false
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