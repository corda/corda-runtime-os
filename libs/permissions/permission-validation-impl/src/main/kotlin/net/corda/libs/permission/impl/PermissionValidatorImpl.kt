package net.corda.libs.permission.impl

import net.corda.data.permissions.Permission
import net.corda.data.permissions.PermissionType
import net.corda.libs.permission.PermissionValidator
import net.corda.libs.permissions.cache.PermissionCache
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug

class PermissionValidatorImpl(
    private val permissionCache: PermissionCache
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

    override fun authorizeUser(requestId: String, loginName: String, permission: String): Boolean {
        logger.debug { "Checking permissions for $permission for user $loginName" }
        val user = permissionCache.getUser(loginName) ?: return false

        if (!user.enabled) {
            logger.debug { "User $loginName is disabled" }
            return false
        }

        return performCheckRec(
            user.roleAssociations.map { it.roleId },
            user.parentGroupId,
            PermissionUrl.fromUrl(permission)
        )
    }

    private tailrec fun performCheckRec(
        roleIds: Collection<String>,
        parentGroupId: String?,
        permissionUrl: PermissionUrl
    ): Boolean {

        logger.debug { "Checking permissions for: $permissionUrl - $roleIds - $parentGroupId" }

        if (roleIds.isEmpty() && parentGroupId == null) {
            logger.debug { "Roles are empty and no parent group left" }
            return false
        }

        // Should we report roles that cannot be found?
        val roles = roleIds.mapNotNull { permissionCache.getRole(it) }

        val permissionRequested: String = permissionUrl.permissionRequested
        val allPermissions = roles.flatMap {
            it.permissions.map { permissionAssociation ->
                requireNotNull(permissionCache.getPermission(permissionAssociation.permissionId)) {
                    "Permission for ${permissionAssociation.permissionId} cannot be found in the cache"
                }
            }
        }

        // Perform checks, with deny taking priority over allow
        val (denies, allows) = allPermissions.partition { it.permissionType == PermissionType.DENY }
        if (denies.any { wildcardMatch(it, permissionRequested) }) {
            logger.debug { "Explicitly denied by: '${denies.first { wildcardMatch(it, permissionRequested) }}'" }
            return false
        }
        if (allows.any { wildcardMatch(it, permissionRequested) }) {
            logger.debug { "Explicitly allowed by: '${allows.first { wildcardMatch(it, permissionRequested) }}'" }
            return true
        }

        // If we could not reach decision yet, try referring to the parent
        if (parentGroupId == null) {
            logger.debug { "No parent group left" }
            return false
        }
        val parentGroup = permissionCache.getGroup(parentGroupId)
        if (parentGroup == null) {
            logger.warn("Group with id: '$parentGroupId' cannot be found")
            return false
        }
        val rolesIdsForGroup = parentGroup.roleAssociations.map { it.roleId }
        return performCheckRec(rolesIdsForGroup, parentGroup.parentGroupId, permissionUrl)
    }

    private fun wildcardMatch(existingPermission: Permission, permissionRequested: String): Boolean {
        return permissionRequested.matches(existingPermission.permissionString.toRegex())
    }
}
