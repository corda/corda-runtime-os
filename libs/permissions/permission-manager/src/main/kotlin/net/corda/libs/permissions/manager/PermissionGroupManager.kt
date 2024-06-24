package net.corda.libs.permissions.manager

import net.corda.libs.permissions.manager.request.CreateGroupRequestDto
import net.corda.libs.permissions.manager.request.CreateUserRequestDto
import net.corda.libs.permissions.manager.response.GroupResponseDto
import net.corda.libs.permissions.manager.response.UserResponseDto

/**
 * The [PermissionGroupManager] provides functionality for managing groups within the permission system.
 */
interface PermissionGroupManager {
    /**
     * Create a user in the RBAC Permission System.
     */
    fun createGroup(createGroupRequestDto: CreateGroupRequestDto): GroupResponseDto
}