package net.corda.libs.permissions.manager

import net.corda.libs.permissions.manager.request.CreateRoleRequestDto
import net.corda.libs.permissions.manager.request.GetRoleRequestDto
import net.corda.libs.permissions.manager.response.RoleResponseDto
import net.corda.v5.base.util.Try

/**
 * The [PermissionRoleManager] provides functionality for managing roles within the permission system.
 */
interface PermissionRoleManager {
    /**
     * Create a role in the RBAC Permission System.
     */
    fun createRole(createRoleRequestDto: CreateRoleRequestDto): Try<RoleResponseDto>

    /**
     * Get a role in the RBAC Permission System identified by its ID.
     */
    fun getRole(roleRequestDto: GetRoleRequestDto): RoleResponseDto?
}