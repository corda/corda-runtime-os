package net.corda.httprpc.test

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource

data class SomeInfo(val id: String, val number: Int)

@HttpRpcResource(name = "NullabilityRPCOps", description = "NullabilityRPCOps", path = "nullability/")
interface NullabilityRPCOps : RpcOps {

    @HttpRpcPOST(path = "postTakesNullableReturnsNullable")
    fun postTakesNullableReturnsNullable(
        @HttpRpcRequestBodyParameter(name = "someInfo")
        someInfo: SomeInfo?
    ): SomeInfo?

    @HttpRpcPOST(path = "postTakesInfoReturnsNullable")
    fun postTakesInfoReturnsNullable(
        @HttpRpcRequestBodyParameter(name = "someInfo")
        someInfo: SomeInfo
    ): SomeInfo?

    @HttpRpcPOST(path = "postTakesNullableReturnsInfo")
    fun postTakesNullableReturnsInfo(
        @HttpRpcRequestBodyParameter(name = "someInfo")
        someInfo: SomeInfo?
    ): SomeInfo

    @HttpRpcPOST(path = "postTakesNullableStringReturnsNullableString")
    fun postTakesNullableStringReturnsNullableString(
        @HttpRpcRequestBodyParameter(name = "input")
        input: String?
    ): String?
}