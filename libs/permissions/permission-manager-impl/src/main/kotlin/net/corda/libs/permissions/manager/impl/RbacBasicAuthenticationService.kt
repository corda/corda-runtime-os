package net.corda.libs.permissions.manager.impl

import net.corda.data.rest.PasswordExpiryStatus
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.management.cache.PermissionManagementCache
import net.corda.libs.permissions.manager.AuthenticationState
import net.corda.libs.permissions.manager.BasicAuthenticationService
import net.corda.permissions.password.PasswordHash
import net.corda.permissions.password.PasswordService
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.debug
import net.corda.utilities.time.Clock
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference

class RbacBasicAuthenticationService(
    rbacConfig: SmartConfig,
    private val clock: Clock,
    private val permissionManagementCacheRef: AtomicReference<PermissionManagementCache?>,
    private val passwordService: PasswordService
) : BasicAuthenticationService {

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val repeatedLogonsCache = RepeatedLogonsCache()

    private val passwordExpiryWarningWindowDays = rbacConfig.getInt(ConfigKeys.RBAC_PASSWORD_EXPIRY_WARNING_WINDOW)

    private val failedAuthentication = AuthenticationState(false, null)

    override fun authenticateUser(loginName: String, password: CharArray): AuthenticationState {
        logger.debug { "Checking authentication for user $loginName." }
        val permissionManagementCache = checkNotNull(permissionManagementCacheRef.get()) {
            "Permission management cache is null."
        }

        val clearTextPassword = String(password)

        if (repeatedLogonsCache.verifies(loginName, clearTextPassword)) {
            return AuthenticationState(true, null)
        }

        val user = permissionManagementCache.getUser(loginName) ?: return failedAuthentication

        if (user.saltValue == null || user.hashedPassword == null) {
            return failedAuthentication
        }

        if (passwordService.verifies(clearTextPassword, PasswordHash(user.saltValue, user.hashedPassword))) {
            val expiryStatus = checkExpiryStatus(user.passwordExpiry)
            // Ensure that only active status if cached
            if (expiryStatus == PasswordExpiryStatus.ACTIVE) {
                repeatedLogonsCache.add(loginName, clearTextPassword)
            } else {
                repeatedLogonsCache.remove(loginName)
            }
            return AuthenticationState(true, expiryStatus)
        }

        repeatedLogonsCache.remove(loginName)
        return failedAuthentication
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

    private fun checkExpiryStatus(passwordExpiry: Instant?): PasswordExpiryStatus {
        val timestamp = clock.instant()
        return when {
            (passwordExpiry == null) -> {
                PasswordExpiryStatus.ACTIVE
            }

            (passwordExpiry >= timestamp) -> {
                // check if the current time is in the warning window for password expiry
                if (timestamp in passwordExpiry.minus(
                        passwordExpiryWarningWindowDays.toLong(),
                        ChronoUnit.DAYS
                    )..passwordExpiry
                ) {
                    PasswordExpiryStatus.CLOSE_TO_EXPIRY
                } else {
                    PasswordExpiryStatus.ACTIVE
                }
            }

            else -> PasswordExpiryStatus.EXPIRED
        }
    }
}
