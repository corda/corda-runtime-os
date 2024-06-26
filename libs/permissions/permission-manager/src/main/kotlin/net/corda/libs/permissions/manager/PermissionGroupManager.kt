package net.corda.libs.permissions.manager

import net.corda.libs.permissions.manager.request.AddRoleToGroupRequestDto
import net.corda.libs.permissions.manager.request.ChangeGroupParentIdDto
import net.corda.libs.permissions.manager.request.CreateGroupRequestDto
import net.corda.libs.permissions.manager.request.DeleteGroupRequestDto
import net.corda.libs.permissions.manager.request.RemoveRoleFromGroupRequestDto
import net.corda.libs.permissions.manager.response.GroupContentResponseDto
import net.corda.libs.permissions.manager.response.GroupResponseDto

/**
 * The [PermissionGroupManager] provides functionality for managing groups within the permission system.
 */
interface PermissionGroupManager {
    /**
     * Create a group in the RBAC Permission System.
     */
    fun createGroup(createGroupRequestDto: CreateGroupRequestDto): GroupResponseDto

    /**
     * Change the parent group of a group in the RBAC Permission System.
     */
    fun changeParentGroup(changeGroupParentIdDto: ChangeGroupParentIdDto): GroupResponseDto

    /**
     * Add a Role to a Group in the RBAC Permission System.
     */
    fun addRoleToGroup(addRoleToGroupRequestDto: AddRoleToGroupRequestDto): GroupResponseDto

    /**
     * Remove a Role from a Group in the RBAC Permission System.
     */
    fun removeRoleFromGroup(removeRoleFromGroupRequestDto: RemoveRoleFromGroupRequestDto): GroupResponseDto

    /**
     * Get a group in the RBAC Permission System identified by `GroupId`.
     */
    fun getGroupContent(groupId: String): GroupContentResponseDto?

    /**
     * Delete a group in the RBAC Permission System identified by `GroupId`.
     */
    fun deleteGroup(deleteGroupRequestDto: DeleteGroupRequestDto): GroupResponseDto
}
