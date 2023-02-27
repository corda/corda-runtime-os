package net.corda.httprpc.test

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.ClientRequestBodyParameter
import net.corda.httprpc.annotations.HttpRestResource

data class SomeInfo(val id: String, val number: Int)

@HttpRestResource(name = "NullabilityRestResource", description = "NullabilityRestResource", path = "nullability/")
interface NullabilityRestResource : RestResource {

    @HttpPOST(path = "postTakesNullableReturnsNullable")
    fun postTakesNullableReturnsNullable(
        @ClientRequestBodyParameter(name = "someInfo")
        someInfo: SomeInfo?
    ): SomeInfo?

    @HttpPOST(path = "postTakesInfoReturnsNullable")
    fun postTakesInfoReturnsNullable(
        @ClientRequestBodyParameter(name = "someInfo")
        someInfo: SomeInfo
    ): SomeInfo?

    @HttpPOST(path = "postTakesNullableReturnsInfo")
    fun postTakesNullableReturnsInfo(
        @ClientRequestBodyParameter(name = "someInfo")
        someInfo: SomeInfo?
    ): SomeInfo

    @HttpPOST(path = "postTakesNullableStringReturnsNullableString")
    fun postTakesNullableStringReturnsNullableString(
        @ClientRequestBodyParameter(name = "input")
        input: String?
    ): String?
}