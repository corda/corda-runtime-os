package net.corda.httprpc.test

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.httprpc.annotations.HttpRpcWS
import net.corda.httprpc.annotations.RPCSinceVersion
import net.corda.httprpc.ws.DuplexChannel
import java.time.Instant
import java.time.ZonedDateTime
import java.util.Date
import java.util.UUID

@HttpRpcResource(name = "HealthCheckAPI", description = "Health Check", path = "health/")
interface TestHealthCheckAPI : RestResource {

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

    @HttpRpcPOST(path = "ping")
    fun ping(@HttpRpcRequestBodyParameter(description = "Data", required = false) pingPongData: PingPongData?): String

    @HttpRpcGET(path = "plusOne", responseDescription = "Increased by one")
    fun plusOne(@HttpRpcQueryParameter(required = false) numbers: List<String> = emptyList()): List<Double>

    @HttpRpcPOST(path = "plusone/{number}", title = "AddOne", description = "Add One")
    fun plus(@HttpRpcPathParameter number: Long): Long

    @HttpRpcPOST(path = "plusdouble", title = "Add One to a Double", description = "Add One to a Double")
    fun plusDouble(@HttpRpcRequestBodyParameter number: Double): Double

    @HttpRpcPOST(path = "bodyPlayground")
    fun bodyPlayground(s1: String?, @HttpRpcRequestBodyParameter(required = false) s2: String?): String

    @HttpRpcPOST(path = "timeCall")
    fun timeCall(@HttpRpcRequestBodyParameter time: TimeCallDto): String

    @HttpRpcPOST(path = "dateCall")
    fun dateCall(@HttpRpcRequestBodyParameter date: DateCallDto): String

    @HttpRpcPOST(path = "instantCall")
    fun instantCall(@HttpRpcRequestBodyParameter instant: InstantCallDto): String

    @HttpRpcGET(path = "throwexception", title = "Throw Exception", description = "Throw an exception")
    fun throwException(@HttpRpcQueryParameter(name = "exception", description = "exception", required = true) exception: String)

    @HttpRpcGET(path = "firstDaysOfTheYear", responseDescription = "Returns first number of days for a given year")
    fun firstDaysOfTheYear(@HttpRpcQueryParameter year: Int, @HttpRpcQueryParameter daysCount: Int): List<DateCallDto>

    @HttpRpcPOST(path = "parseuuid/{uuid}", description = "https://r3-cev.atlassian.net/browse/CORE-2404 coverage")
    fun parseUuid(@HttpRpcPathParameter uuid: String): UUID

    @HttpRpcPOST(path = "stringMethodWithNameInAnnotation")
    fun stringMethodWithNameInAnnotation(@HttpRpcRequestBodyParameter(name = "correctName") incorrectName: String): String

    data class SomeTestNullableType(val number: Int, val str: String)
    data class ObjectWithNullableString(val str: String?)

    @HttpRpcPOST(path = "apiReturningNullObject")
    fun apiReturningNullObject(): SomeTestNullableType?

    @HttpRpcPOST(path = "apiReturningNullString")
    fun apiReturningNullString(): String?

    @HttpRpcPOST(path = "apiReturningObjectWithNullableStringInside")
    fun apiReturningObjectWithNullableStringInside(): ObjectWithNullableString

    data class TimeCallDto(val time: ZonedDateTime)

    data class DateCallDto(val date: Date)

    data class InstantCallDto(val instant: Instant)

    class PingPongData(@JsonDeserialize(using = PingPongDataDeserializer::class) val str: String)
    class PingPongDataDeserializer : JsonDeserializer<String>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): String {
            return "str = ${p.text}"
        }
    }

    @HttpRpcGET(path = "laterAddedCall")
    @Suppress("MagicNumber")
    @RPCSinceVersion(3)
    fun laterAddedCall(): String

    @HttpRpcGET(path = "echoQuery", description = "Echoes what has been supplied in the query")
    fun echoQuery(@HttpRpcQueryParameter requestString: String): String

    @HttpRpcGET(path = "echoPath/{requestString}", description = "Echoes what has been supplied in the path")
    fun echoPath(@HttpRpcPathParameter(name = "requestString", description = "The name") requestString: String): String

    @HttpRpcWS(path = "counterFeed/{start}", responseDescription = "Given number supplied produces a WebSocket feed incrementing it")
    fun counterFeed(
        channel: DuplexChannel,
        @HttpRpcPathParameter start: Int,
        @HttpRpcQueryParameter(required = false) range: Int?
    )
}
