package net.corda.libs.permissions.manager.impl.converter

import net.corda.data.permissions.PermissionAssociation as AvroPermissionAssociation
import net.corda.libs.permissions.manager.response.PermissionResponseDto
import net.corda.libs.permissions.manager.response.PropertyResponseDto
import net.corda.libs.permissions.manager.response.RoleResponseDto
import net.corda.libs.permissions.manager.response.UserResponseDto
import net.corda.data.permissions.User as AvroUser
import net.corda.data.permissions.Role as AvroRole

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

fun AvroPermissionAssociation.convertToResponseDto() : PermissionResponseDto {
    return PermissionResponseDto(
        permission.id,
        permission.version,
        permission.lastChangeDetails.updateTimestamp,
        permission.groupVisibility,
        permission.virtualNode,
        permission.type.name,
        permission.permissionString
    )
}