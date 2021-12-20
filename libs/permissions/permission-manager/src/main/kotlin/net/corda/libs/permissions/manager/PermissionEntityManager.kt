package net.corda.libs.permissions.manager

import net.corda.libs.permissions.manager.request.CreatePermissionRequestDto
import net.corda.libs.permissions.manager.request.GetPermissionRequestDto
import net.corda.libs.permissions.manager.response.PermissionResponseDto

/**
 * The [PermissionEntityManager] provides functionality for managing permission entities within the permission system.
 */
interface PermissionEntityManager {
    /**
     * Create a permission entity in the RBAC Permission System.
     */
    fun createPermission(createPermissionRequestDto: CreatePermissionRequestDto): PermissionResponseDto

    /**
     * Get a permission entity in the RBAC Permission System identified by its ID.
     */
    fun getPermission(permissionRequestDto: GetPermissionRequestDto): PermissionResponseDto?
}