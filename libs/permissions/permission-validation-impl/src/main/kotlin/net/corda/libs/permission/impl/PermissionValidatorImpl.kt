package net.corda.libs.permission.impl

import net.corda.data.permissions.summary.UserPermissionSummary
import net.corda.libs.permission.PermissionValidator
import net.corda.libs.permissions.validation.cache.PermissionValidationCache
import net.corda.v5.base.util.debug
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference
import net.corda.data.permissions.PermissionType as AvroPermissionType

class PermissionValidatorImpl(
    private val permissionValidationCacheRef: AtomicReference<PermissionValidationCache?>
) : PermissionValidator {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
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

    override fun authorizeUser(loginName: String, operation: String): Boolean {
        logger.debug { "Checking permissions for $operation for user $loginName" }

        val permissionValidationCache = checkNotNull(permissionValidationCacheRef.get()) {
            "Permission validation cache is null."
        }

        val permissionSummary = permissionValidationCache.getPermissionSummary(loginName)

        if (permissionSummary == null) {
            logger.debug { "No permission found for user $loginName." }
            return false
        }

        if (!permissionSummary.enabled) {
            logger.debug { "User $loginName is disabled" }
            return false
        }

        logger.debug { "Permission summary found for user $loginName with permissions: ${permissionSummary.permissions.joinToString()}." }

        return findPermissionMatch(permissionSummary, operation)
    }

    private fun findPermissionMatch(permissionSummary: UserPermissionSummary, operation: String): Boolean {

        val (denies, allows) = permissionSummary.permissions
            .partition { it.permissionType == AvroPermissionType.DENY }

        val maybeFirstDeny = denies.firstOrNull { wildcardMatch(it.permissionString, operation) }
        if (maybeFirstDeny != null) {
            logger.debug { "Explicitly denied by: '$maybeFirstDeny'" }
            return false
        }

        val maybeFirstAllow = allows.firstOrNull { wildcardMatch(it.permissionString, operation) }
        if (maybeFirstAllow != null) {
            logger.debug { "Explicitly allowed by: '$maybeFirstAllow'" }
            return true
        }

        logger.debug { "No deny or allow found - denying" }
        return false
    }

    private fun wildcardMatch(permissionString: String, permissionRequested: String): Boolean {
        return permissionRequested.matches(permissionString.toRegex(RegexOption.IGNORE_CASE))
    }
}
