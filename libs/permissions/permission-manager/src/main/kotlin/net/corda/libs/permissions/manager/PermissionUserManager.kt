package net.corda.libs.permissions.manager

import net.corda.libs.permissions.manager.request.AddPropertyToUserRequestDto
import net.corda.libs.permissions.manager.request.AddRoleToUserRequestDto
import net.corda.libs.permissions.manager.request.ChangeUserPasswordDto
import net.corda.libs.permissions.manager.request.CreateUserRequestDto
import net.corda.libs.permissions.manager.request.DeleteUserRequestDto
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
     * Delete a user in the RBAC Permission System.
     */
    fun deleteUser(deleteUserRequestDto: DeleteUserRequestDto): UserResponseDto

    /**
     * Change a user's own password.
     */
    fun changeUserPasswordSelf(changeUserPasswordDto: ChangeUserPasswordDto): UserResponseDto

    /**
     * Change another user's password. Only valid for admin user.
     */
    fun changeUserPasswordOther(changeUserPasswordDto: ChangeUserPasswordDto): UserResponseDto

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

    /**
     * Add a property to a User in the RBAC Permission System.
     */
    fun addPropertyToUser(addPropertyToUserRequestDto: AddPropertyToUserRequestDto): UserResponseDto
}
