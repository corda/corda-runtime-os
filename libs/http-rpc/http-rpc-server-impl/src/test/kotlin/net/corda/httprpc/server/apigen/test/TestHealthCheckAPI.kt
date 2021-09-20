package net.corda.httprpc.server.apigen.test

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.httprpc.api.RpcOps
import net.corda.v5.httprpc.api.annotations.HttpRpcGET
import net.corda.v5.httprpc.api.annotations.HttpRpcPOST
import net.corda.v5.httprpc.api.annotations.HttpRpcPathParameter
import net.corda.v5.httprpc.api.annotations.HttpRpcQueryParameter
import net.corda.v5.httprpc.api.annotations.HttpRpcRequestBodyParameter
import net.corda.v5.httprpc.api.annotations.HttpRpcResource
import net.corda.v5.httprpc.api.annotations.RPCSinceVersion
import java.time.Instant
import java.time.ZonedDateTime
import java.util.*

@HttpRpcResource(name = "HealthCheckAPI", description = "Health Check", path = "health/")
interface TestHealthCheckAPI : RpcOps {

    @HttpRpcGET(path = "sanity", title = "Sanity", description = "Sanity endpoint")
    fun void(): String

    @HttpRpcGET(path = "void", title = "Void", description = "Void endpoint")
    fun voidResponse()

    @HttpRpcGET(path = "hello2/{name}", title = "Hello2", description = "Hello endpoint")
    fun hello2(
        @HttpRpcQueryParameter(name = "id", description = "id", required = false) queryParam: String?,
        @HttpRpcPathParameter(name = "name", description = "The name") pathParam: String
    ): String

    @HttpRpcGET(path = "hello/{name}", title = "Hello", description = "Hello endpoint")
    fun hello(
        @HttpRpcPathParameter(name = "name", description = "The name") pathParam: String,
        @HttpRpcQueryParameter(name = "id", description = "id", required = false) param: Int?
    ): String

    @HttpRpcPOST
    fun ping(@HttpRpcRequestBodyParameter(description = "Data", required = false) pingPongData: PingPongData?): String

    @HttpRpcGET(responseDescription = "Increased by one")
    fun plusOne(@HttpRpcQueryParameter(required = false) numbers: List<String> = emptyList()): List<Double>

    @HttpRpcPOST(path = "plusone/{number}", title = "AddOne", description = "Add One")
    fun plus(@HttpRpcPathParameter number: Long): Long

    @HttpRpcPOST(path = "plusdouble", title = "Add One to a Double", description = "Add One to a Double")
    fun plusDouble(@HttpRpcRequestBodyParameter number: Double): Double

    @HttpRpcPOST
    fun bodyPlayground(s1: String?, @HttpRpcRequestBodyParameter(required = false) s2: String?): String

    @HttpRpcPOST
    fun timeCall(@HttpRpcRequestBodyParameter time: TimeCallDto): String

    @HttpRpcPOST
    fun dateCall(@HttpRpcRequestBodyParameter date: DateCallDto): String

    @HttpRpcPOST
    fun instantCall(@HttpRpcRequestBodyParameter instant: InstantCallDto): String

    @HttpRpcGET(path = "throwexception", title = "Throw Exception", description = "Throw an exception")
    fun throwException(@HttpRpcQueryParameter(name = "exception", description = "exception", required = true) exception: String)

    @HttpRpcGET(responseDescription = "Returns first number of days for a given year")
    fun firstDaysOfTheYear(@HttpRpcQueryParameter year: Int, @HttpRpcQueryParameter daysCount: Int): List<DateCallDto>

    @HttpRpcPOST(path = "parseuuid/{uuid}", description = "https://r3-cev.atlassian.net/browse/CORE-2404 coverage")
    fun parseUuid(@HttpRpcPathParameter uuid: String): UUID

    @CordaSerializable
    data class TimeCallDto(val time: ZonedDateTime)

    @CordaSerializable
    data class DateCallDto(val date: Date)

    @CordaSerializable
    data class InstantCallDto(val instant: Instant)

    @CordaSerializable
    class PingPongData(@JsonDeserialize(using = PingPongDataDeserializer::class) val str: String)
    class PingPongDataDeserializer : JsonDeserializer<String>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): String {
            return "str = ${p.text}"
        }
    }

    @HttpRpcGET
    @Suppress("MagicNumber")
    @RPCSinceVersion(3)
    fun laterAddedCall(): String
}
