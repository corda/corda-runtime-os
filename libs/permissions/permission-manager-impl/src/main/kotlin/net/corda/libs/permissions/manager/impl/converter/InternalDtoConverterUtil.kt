package net.corda.libs.permissions.manager.impl.converter

import net.corda.data.permissions.management.permission.BulkCreatePermissionsResponse
import net.corda.data.permissions.management.permission.CreatePermissionRequest
import net.corda.libs.permissions.manager.common.PermissionTypeDto
import net.corda.libs.permissions.manager.request.CreatePermissionRequestDto
import net.corda.libs.permissions.manager.response.GroupResponseDto
import net.corda.libs.permissions.manager.response.PermissionAssociationResponseDto
import net.corda.libs.permissions.manager.response.PermissionResponseDto
import net.corda.libs.permissions.manager.response.PermissionSummaryResponseDto
import net.corda.libs.permissions.manager.response.PermissionsResponseDto
import net.corda.libs.permissions.manager.response.PropertyResponseDto
import net.corda.libs.permissions.manager.response.RoleAssociationResponseDto
import net.corda.libs.permissions.manager.response.RoleResponseDto
import net.corda.libs.permissions.manager.response.UserResponseDto
import net.corda.data.permissions.Group as AvroGroup
import net.corda.data.permissions.Permission as AvroPermission
import net.corda.data.permissions.PermissionAssociation as AvroPermissionAssociation
import net.corda.data.permissions.PermissionType as AvroPermissionType
import net.corda.data.permissions.Role as AvroRole
import net.corda.data.permissions.User as AvroUser
import net.corda.data.permissions.summary.PermissionSummary as AvroPermissionSummary

/**
 * Avro objects are versioned, serialized and put onto the messaging bus.
 *
 * ResponseDtos are not versioned, used internally for passing around data between classes and components.
 *
 * This Utility class provides functions for converting:
 *
 * - From Avro objects to ResponseDtos.
 * - From ResponseDTOs to Avro objects.
 */

/**
 * Convert the Avro User object to the internal UserResponseDto.
 */
fun AvroUser.convertToResponseDto(): UserResponseDto {
    return UserResponseDto(
        id,
        version,
        lastChangeDetails.updateTimestamp,
        fullName,
        loginName,
        enabled,
        ssoAuth,
        passwordExpiry,
        parentGroupId,
        properties.map {
            PropertyResponseDto(
                it.lastChangeDetails.updateTimestamp,
                it.key,
                it.value
            )
        },
        roleAssociations.map {
            RoleAssociationResponseDto(it.roleId, it.changeDetails.updateTimestamp)
        }
    )
}

fun AvroRole.convertToResponseDto(): RoleResponseDto {
    return RoleResponseDto(
        id,
        version,
        lastChangeDetails.updateTimestamp,
        name,
        groupVisibility,
        permissions.map { it.convertToResponseDto() }
    )
}

fun AvroGroup.convertToResponseDto(): GroupResponseDto {
    return GroupResponseDto(
        id,
        lastChangeDetails.updateTimestamp,
        name,
        parentGroupId,
        properties.map {
            PropertyResponseDto(
                it.lastChangeDetails.updateTimestamp,
                it.key,
                it.value
            )
        },
        roleAssociations.map {
            RoleAssociationResponseDto(it.roleId, it.changeDetails.updateTimestamp)
        }
    )
}

fun AvroPermissionAssociation.convertToResponseDto(): PermissionAssociationResponseDto {
    return PermissionAssociationResponseDto(
        permissionId,
        changeDetails.updateTimestamp
    )
}

private fun AvroPermissionType.toResponseDtoType(): PermissionTypeDto {
    return when (this) {
        AvroPermissionType.ALLOW -> PermissionTypeDto.ALLOW
        AvroPermissionType.DENY -> PermissionTypeDto.DENY
    }
}

fun CreatePermissionRequestDto.convertToAvro(): CreatePermissionRequest {
    return CreatePermissionRequest(
        permissionType.toAvroType(),
        permissionString,
        groupVisibility
    )
}

fun PermissionTypeDto.toAvroType(): AvroPermissionType {
    return when (this) {
        PermissionTypeDto.ALLOW -> AvroPermissionType.ALLOW
        PermissionTypeDto.DENY -> AvroPermissionType.DENY
    }
}

fun AvroPermission.convertToResponseDto(): PermissionResponseDto {
    return PermissionResponseDto(
        id,
        version,
        lastChangeDetails.updateTimestamp,
        groupVisibility,
        virtualNode,
        permissionType.toResponseDtoType(),
        permissionString
    )
}

fun AvroPermissionSummary.convertToResponseDto(): PermissionSummaryResponseDto {
    return PermissionSummaryResponseDto(
        id,
        groupVisibility,
        virtualNode,
        permissionType.toResponseDtoType(),
        permissionString
    )
}

fun BulkCreatePermissionsResponse.convertToResponseDto(): PermissionsResponseDto {
    return PermissionsResponseDto(permissionIds.toSet(), roleIds.toSet())
}
