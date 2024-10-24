package net.corda.libs.permissions.manager

import net.corda.libs.permissions.manager.request.AddPropertyToUserRequestDto
import net.corda.libs.permissions.manager.request.AddRoleToUserRequestDto
import net.corda.libs.permissions.manager.request.ChangeUserParentIdDto
import net.corda.libs.permissions.manager.request.ChangeUserPasswordDto
import net.corda.libs.permissions.manager.request.CreateUserRequestDto
import net.corda.libs.permissions.manager.request.DeleteUserRequestDto
import net.corda.libs.permissions.manager.request.GetPermissionSummaryRequestDto
import net.corda.libs.permissions.manager.request.GetUserPropertiesRequestDto
import net.corda.libs.permissions.manager.request.GetUserRequestDto
import net.corda.libs.permissions.manager.request.GetUsersByPropertyRequestDto
import net.corda.libs.permissions.manager.request.RemovePropertyFromUserRequestDto
import net.corda.libs.permissions.manager.request.RemoveRoleFromUserRequestDto
import net.corda.libs.permissions.manager.response.PropertyResponseDto
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
     * Change the parent group of a user in the RBAC Permission System.
     */
    fun changeUserParentGroup(changeUserParentGroupIdDto: ChangeUserParentIdDto): UserResponseDto

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

    /**
     * Remove a property from a User in the RBAC Permission System.
     */
    fun removePropertyFromUser(removePropertyFromUserRequestDto: RemovePropertyFromUserRequestDto): UserResponseDto

    /**
     * Get properties of a User in the RBAC Permission System.
     */
    fun getUserProperties(getUserPropertiesRequestDto: GetUserPropertiesRequestDto): Set<PropertyResponseDto>

    /**
     * Get all the users with a given property in the RBAC Permission System.
     */
    fun getUsersByProperty(getUsersByPropertyRequestDto: GetUsersByPropertyRequestDto): Set<UserResponseDto>
}
