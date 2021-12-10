package net.corda.libs.permissions.endpoints.v1.converter

import net.corda.libs.permissions.endpoints.v1.permission.types.PermissionResponseType
import net.corda.libs.permissions.endpoints.v1.role.types.CreateRoleRequestType
import net.corda.libs.permissions.endpoints.v1.role.types.RoleResponseType
import net.corda.libs.permissions.endpoints.v1.user.types.CreateUserRequestType
import net.corda.libs.permissions.endpoints.v1.user.types.PropertyResponseType
import net.corda.libs.permissions.endpoints.v1.user.types.UserResponseType
import net.corda.libs.permissions.manager.request.CreateRoleRequestDto
import net.corda.libs.permissions.manager.request.CreateUserRequestDto
import net.corda.libs.permissions.manager.response.PermissionResponseDto
import net.corda.libs.permissions.manager.response.PropertyResponseDto
import net.corda.libs.permissions.manager.response.RoleResponseDto
import net.corda.libs.permissions.manager.response.UserResponseDto

/**
 * RequestTypes and ResponseTypes are versioned classes that make up part of the public HTTP API.
 *
 * RequestTypes are unmarshalled from request payloads.
 *
 * ResponseTypes are marshalled and returned to the HTTP caller.
 *
 * ResponseDtos are not versioned and are used internally for passing around data between classes and components.
 *
 * This Utility class provides functions for converting:
 *
 * - From RequestTypes to ResponseDtos.
 * - From ResponseDTOs to ResponseTypes.
 */

/**
 * Convert a CreateUserRequestType to a CreateUserRequestDto to be used internally for passing around data.
 */
fun CreateUserRequestType.convertToDto(requestedBy: String): CreateUserRequestDto {
    return CreateUserRequestDto(
        requestedBy,
        fullName,
        loginName,
        enabled,
        initialPassword,
        passwordExpiry,
        parentGroup,
    )
}

/**
 * Convert a CreateRoleRequestType to a CreateRoleRequestDto to be used internally for passing around data.
 */
fun CreateRoleRequestType.convertToDto(requestedBy: String): CreateRoleRequestDto {
    return CreateRoleRequestDto(
        requestedBy,
        roleName,
        groupVisibility
    )
}

/**
 * Convert a UserResponseDto to a v1 UserResponseType to be returned to the HTTP caller.
 */
fun UserResponseDto.convertToEndpointType(): UserResponseType {
    return UserResponseType(
        id,
        version,
        lastUpdatedTimestamp,
        fullName,
        loginName,
        enabled,
        ssoAuth,
        passwordExpiry,
        parentGroup,
        properties.map { it.convertToEndpointType() }
    )
}

/**
 * Convert a PropertyResponseDto to a v1 PropertyResponseType to be returned to the HTTP caller.
 */
fun PropertyResponseDto.convertToEndpointType(): PropertyResponseType {
    return PropertyResponseType(
        lastChangedTimestamp,
        key,
        value
    )
}

/**
 * Convert a RoleResponseDto to a v1 RoleResponseType to be returned to the HTTP caller.
 */
fun RoleResponseDto.convertToEndpointType(): RoleResponseType {
    return RoleResponseType(
        id,
        version,
        lastUpdatedTimestamp,
        roleName,
        groupVisibility,
        permissions.map { it.convertToEndpointType() }
    )
}

/**
 * Convert a PermissionResponseDto to a v1 PermissionResponseType to be returned to the HTTP caller.
 */
fun PermissionResponseDto.convertToEndpointType(): PermissionResponseType {
    return PermissionResponseType(
        id,
        version,
        lastUpdatedTimestamp,
        groupVisibility,
        virtualNode,
        permissionType,
        permissionString
    )
}
