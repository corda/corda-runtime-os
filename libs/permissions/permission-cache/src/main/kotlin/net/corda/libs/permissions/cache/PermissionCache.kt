package net.corda.libs.permissions.cache

import net.corda.data.permissions.Group
import net.corda.data.permissions.Permission
import net.corda.data.permissions.Role
import net.corda.data.permissions.User
import net.corda.lifecycle.Lifecycle

/**
 * This interface defines a permission cache capable of maintaining a lifecycle and returning User, Group and Role data.
 */
interface PermissionCache : Lifecycle {
    val users: Map<String, User>
    val groups: Map<String, Group>
    val roles: Map<String, Role>

    fun getUser(loginName: String): User?
    fun getGroup(groupId: String): Group?
    fun getRole(roleId: String): Role?
    fun getPermission(permissionId: String): Permission?
}