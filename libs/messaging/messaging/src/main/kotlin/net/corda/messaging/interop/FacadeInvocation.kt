package net.corda.messaging.interop

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.MemberX500Name

@CordaSerializable
data class FacadeInvocation(
    val memberName: MemberX500Name,
    val facadeId: String,
    val methodName: String,
    val payload: String
)

@CordaSerializable
data class FacadeInvocationResult(
    val result: String
)
