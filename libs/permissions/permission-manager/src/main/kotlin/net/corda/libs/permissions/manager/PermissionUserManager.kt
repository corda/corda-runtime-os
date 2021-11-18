package net.corda.libs.permissions.manager

import net.corda.libs.permissions.manager.request.CreateUserRequestDto
import net.corda.libs.permissions.manager.request.GetUserRequestDto
import net.corda.libs.permissions.manager.response.UserResponseDto
import net.corda.v5.base.util.Try

/**
 * The [PermissionUserManager] provides functionality for managing users within the permission system.
 */
interface PermissionUserManager {
    /**
     * Create a user in the RBAC Permission System.
     */
    fun createUser(createUserRequestDto: CreateUserRequestDto): Try<UserResponseDto>

    /**
     * Get a user in the RBAC Permission System identified by `LoginName`.
     */
    fun getUser(userRequestDto: GetUserRequestDto): UserResponseDto?
}