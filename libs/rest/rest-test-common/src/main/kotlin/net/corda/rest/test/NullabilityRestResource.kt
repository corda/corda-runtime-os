package net.corda.rest.test

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.ClientRequestBodyParameter
import net.corda.rest.annotations.HttpRestResource

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