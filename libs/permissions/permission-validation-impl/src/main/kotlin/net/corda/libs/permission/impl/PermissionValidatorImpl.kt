package net.corda.libs.permission.impl

import net.corda.data.permissions.summary.UserPermissionSummary
import net.corda.data.permissions.PermissionType as AvroPermissionType
import net.corda.libs.permission.PermissionValidator
import net.corda.libs.permissions.validation.cache.PermissionValidationCache
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug

class PermissionValidatorImpl(
    private val permissionValidationCache: PermissionValidationCache
) : PermissionValidator {

    companion object {
        private val logger = contextLogger()
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

    override fun authorizeUser(loginName: String, permission: String): Boolean {
        logger.debug { "Checking permissions for $permission for user $loginName" }
        val permissionSummary = permissionValidationCache.getPermissionSummary(loginName)

        if (permissionSummary == null) {
            logger.debug { "No permission found for user $loginName." }
            return false
        }

        if (!permissionSummary.enabled) {
            logger.debug { "User $loginName is disabled" }
            return false
        }

        if (!permissionSummary.enabled) {
            logger.debug { "User $loginName is disabled" }
            return false
        }

        logger.debug { "Permission summary found for user $loginName with permissions: ${permissionSummary.permissions.joinToString()}." }

        return findPermissionMatch(permissionSummary, permission)
    }

    private fun findPermissionMatch(permissionSummary: UserPermissionSummary, permission: String): Boolean {

        val (denies, allows) = permissionSummary.permissions
            .partition { it.permissionType == AvroPermissionType.DENY }

        val maybeFirstDeny = denies.firstOrNull { wildcardMatch(it.permissionString, permission) }
        if (maybeFirstDeny != null) {
            logger.debug { "Explicitly denied by: '$maybeFirstDeny'" }
            return false
        }

        val maybeFirstAllow = allows.firstOrNull { wildcardMatch(it.permissionString, permission) }
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
