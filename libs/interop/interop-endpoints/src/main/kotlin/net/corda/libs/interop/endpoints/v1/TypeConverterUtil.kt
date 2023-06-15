//package net.corda.libs.interop.endpoints.v1.converter
//
//import net.corda.libs.interop.endpoints.v1.CreateInteropIdentityRequestDto
//import net.corda.libs.interop.endpoints.v1.InteropIdentityResponseDto
//import net.corda.libs.interop.endpoints.v1.types.CreateInterOpIdentityType
//import net.corda.libs.interop.endpoints.v1.types.InteropIdentityResponseType
//
///**
// * Convert a RoleResponseDto to a v1 RoleResponseType to be returned to the HTTP caller.
// */
//fun InteropIdentityResponseDto.convertToEndpointType(): InteropIdentityResponseType {
//    return InteropIdentityResponseType(
//        x500Name,
//        groupId
//    )
//}
//
//fun CreateInterOpIdentityType.convertToDto(requestedBy: String): CreateInteropIdentityRequestDto {
//    return CreateInteropIdentityRequestDto(
//        requestedBy,
//        x500Name,
//        groupId.toString()
//    )
//}