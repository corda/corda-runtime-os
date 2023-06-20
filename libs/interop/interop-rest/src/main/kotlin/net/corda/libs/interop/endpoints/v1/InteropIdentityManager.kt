package net.corda.libs.interop.endpoints.v1

import net.corda.libs.interop.endpoints.v1.types.CreateInteropIdentityRequest

interface InteropIdentityManager {
    fun createInteropIdentity(createInteropIdentityRequestDto: CreateInteropIdentityRequestDto): CreateInteropIdentityRequest
}