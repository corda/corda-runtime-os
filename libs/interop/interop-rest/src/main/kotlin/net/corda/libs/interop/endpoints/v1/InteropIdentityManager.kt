package net.corda.libs.interop.endpoints.v1

interface InteropIdentityManager {
    fun createInteropIdentity(createInteropIdentityRequestDto: CreateInteropIdentityRequestDto): InteropIdentityResponseDto
}