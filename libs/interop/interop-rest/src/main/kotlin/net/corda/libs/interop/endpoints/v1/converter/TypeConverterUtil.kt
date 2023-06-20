package net.corda.libs.interop.endpoints.v1.converter

import net.corda.libs.interop.endpoints.v1.CreateInteropIdentityRequestDto
import net.corda.libs.interop.endpoints.v1.types.CreateInteropIdentityRequest

/**
 * Convert a CreateRoleRequestType to a CreateRoleRequestDto to be used internally for passing around data.
 */
fun CreateInteropIdentityRequest.convertToDto(requestedBy: String): CreateInteropIdentityRequestDto {
    return CreateInteropIdentityRequestDto(
        requestedBy,
        x500Name,
        groupId.toString()
    )
}

/**
 * Convert a UserResponseDto to a v1 UserResponseType to be returned to the HTTP caller.
 */
//fun InteropIdentityResponseType.convertToEndpointType(): InteropIdentityResponseType {
//    return InteropIdentityResponseType(
//        x500Name,
//        groupId
//    )
//}

