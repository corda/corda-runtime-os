package net.corda.permissions.query.dto

import net.corda.permissions.model.PermissionType

/**
 * Internal permission query data transfer object holding the summary data for one permission.
 */
data class InternalPermissionQueryDto(
    val loginName: String,
    val id: String,
    val groupVisibility: String?,
    val virtualNode: String?,
    val permissionString: String,
    val permissionType: PermissionType
)

/**
 * Internal permission query data transfer object holding data for one permission, its associated parent group and user/group id.
 * [loginName] will be null if it is a group.
 */
data class InternalPermissionWithParentGroupQueryDto(
    val id: String,
    val permissionId: String?,
    val groupVisibility: String?,
    val virtualNode: String?,
    val permissionString: String?,
    val permissionType: PermissionType?,
    val parentGroupId: String?,
    val loginName: String?
)

data class InternalUserGroup(
    val id: String,
    val parentId: String?,
    val loginName: String?,
    val permissionsList: List<Permission>
)

data class Permission(
    val id: String,
    val groupVisibility: String?,
    val virtualNode: String?,
    val permissionString: String,
    val permissionType: PermissionType
)

/**
 * Internal permission query data transfer object holding the login name and if user is enabled.
 */
data class InternalUserEnabledQueryDto(
    val loginName: String,
    val enabled: Boolean
)
