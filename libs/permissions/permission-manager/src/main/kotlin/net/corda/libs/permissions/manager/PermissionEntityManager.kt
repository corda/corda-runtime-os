package net.corda.libs.permissions.manager

import net.corda.libs.permissions.manager.request.CreatePermissionRequestDto
import net.corda.libs.permissions.manager.request.CreatePermissionsRequestDto
import net.corda.libs.permissions.manager.request.GetPermissionRequestDto
import net.corda.libs.permissions.manager.request.QueryPermissionsRequestDto
import net.corda.libs.permissions.manager.response.PermissionResponseDto
import net.corda.libs.permissions.manager.response.PermissionsResponseDto

/**
 * The [PermissionEntityManager] provides functionality for managing permission entities within the permission system.
 */
interface PermissionEntityManager {
    /**
     * Create a permission entity in the RBAC Permission System.
     */
    fun createPermission(createPermissionRequestDto: CreatePermissionRequestDto): PermissionResponseDto

    /**
     * Creates multiple permissions and optionally assigns them to a set of existing roles
     */
    fun createPermissions(createPermissionsRequestDto: CreatePermissionsRequestDto): PermissionsResponseDto

    /**
     * Get a permission entity in the RBAC Permission System identified by its ID.
     */
    fun getPermission(permissionRequestDto: GetPermissionRequestDto): PermissionResponseDto?

    /**
     * Get permissions matching query.
     */
    fun queryPermissions(permissionsQuery: QueryPermissionsRequestDto): List<PermissionResponseDto>
}
