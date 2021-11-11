package net.corda.libs.permissions.cache

import net.corda.data.permissions.Group
import net.corda.data.permissions.Role
import net.corda.data.permissions.User
import net.corda.lifecycle.Lifecycle

interface PermissionCache : Lifecycle {
    fun getUser(loginName: String): User?
    fun getGroup(groupId: String): Group?
    fun getRole(roleId: String): Role?

    fun getUsers(): Map<String, User>
    fun getGroups(): Map<String, Group>
    fun getRoles(): Map<String, Role>
}