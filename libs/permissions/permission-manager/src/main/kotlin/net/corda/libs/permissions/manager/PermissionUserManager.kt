package net.corda.libs.permissions.manager

import net.corda.libs.permissions.manager.request.AddRoleToUserRequestDto
import net.corda.libs.permissions.manager.request.CreateUserRequestDto
import net.corda.libs.permissions.manager.request.GetPermissionSummaryRequestDto
import net.corda.libs.permissions.manager.request.GetUserRequestDto
import net.corda.libs.permissions.manager.request.RemoveRoleFromUserRequestDto
import net.corda.libs.permissions.manager.response.UserPermissionSummaryResponseDto
import net.corda.libs.permissions.manager.response.UserResponseDto

/**
 * The [PermissionUserManager] provides functionality for managing users within the permission system.
 */
interface PermissionUserManager {
    /**
     * Create a user in the RBAC Permission System.
     */
    fun createUser(createUserRequestDto: CreateUserRequestDto): UserResponseDto

    /**
     * Get a user in the RBAC Permission System identified by `LoginName`.
     */
    fun getUser(userRequestDto: GetUserRequestDto): UserResponseDto?

    /**
     * Add a Role to a User in the RBAC Permission System.
     */
    fun addRoleToUser(addRoleToUserRequestDto: AddRoleToUserRequestDto): UserResponseDto

    /**
     * Remove a Role from a User in the RBAC Permission System.
     */
    fun removeRoleFromUser(removeRoleFromUserRequestDto: RemoveRoleFromUserRequestDto): UserResponseDto

    /**
     * Get a summary of a user's permissions in the RBAC Permission System identified by `LoginName`.
     *
     * If the user does not exist then return null.
     */
    fun getPermissionSummary(permissionSummaryRequestDto: GetPermissionSummaryRequestDto): UserPermissionSummaryResponseDto?
}