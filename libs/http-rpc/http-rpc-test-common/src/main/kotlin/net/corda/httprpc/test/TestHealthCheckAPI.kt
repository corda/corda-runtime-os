package net.corda.httprpc.test

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpGET
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.RestPathParameter
import net.corda.httprpc.annotations.RestQueryParameter
import net.corda.httprpc.annotations.RestRequestBodyParameter
import net.corda.httprpc.annotations.HttpRestResource
import net.corda.httprpc.annotations.HttpWS
import net.corda.httprpc.annotations.RestSinceVersion
import net.corda.httprpc.ws.DuplexChannel
import java.time.Instant
import java.time.ZonedDateTime
import java.util.Date
import java.util.UUID

@HttpRestResource(name = "HealthCheckAPI", description = "Health Check", path = "health/")
interface TestHealthCheckAPI : RestResource {

    @HttpGET(path = "sanity", title = "Sanity", description = "Sanity endpoint")
    fun void(): String

    @HttpGET(path = "void", title = "Void", description = "Void endpoint")
    fun voidResponse()

    @HttpGET(path = "hello2/{name}", title = "Hello2", description = "Hello endpoint")
    fun hello2(
        @RestQueryParameter(name = "id", description = "id", required = false) queryParam: String?,
        @RestPathParameter(name = "name", description = "The name") pathParam: String
    ): String

    @HttpGET(path = "hello/{name}", title = "Hello", description = "Hello endpoint")
    fun hello(
        @RestPathParameter(name = "name", description = "The name") pathParam: String,
        @RestQueryParameter(name = "id", description = "id", required = false) param: Int?
    ): String

    @HttpPOST(path = "ping")
    fun ping(@RestRequestBodyParameter(description = "Data", required = false) pingPongData: PingPongData?): String

    @HttpGET(path = "plusOne", responseDescription = "Increased by one")
    fun plusOne(@RestQueryParameter(required = false) numbers: List<String> = emptyList()): List<Double>

    @HttpPOST(path = "plusone/{number}", title = "AddOne", description = "Add One")
    fun plus(@RestPathParameter number: Long): Long

    @HttpPOST(path = "plusdouble", title = "Add One to a Double", description = "Add One to a Double")
    fun plusDouble(@RestRequestBodyParameter number: Double): Double

    @HttpPOST(path = "bodyPlayground")
    fun bodyPlayground(s1: String?, @RestRequestBodyParameter(required = false) s2: String?): String

    @HttpPOST(path = "timeCall")
    fun timeCall(@RestRequestBodyParameter time: TimeCallDto): String

    @HttpPOST(path = "dateCall")
    fun dateCall(@RestRequestBodyParameter date: DateCallDto): String

    @HttpPOST(path = "instantCall")
    fun instantCall(@RestRequestBodyParameter instant: InstantCallDto): String

    @HttpGET(path = "throwexception", title = "Throw Exception", description = "Throw an exception")
    fun throwException(@RestQueryParameter(name = "exception", description = "exception", required = true) exception: String)

    @HttpGET(path = "firstDaysOfTheYear", responseDescription = "Returns first number of days for a given year")
    fun firstDaysOfTheYear(@RestQueryParameter year: Int, @RestQueryParameter daysCount: Int): List<DateCallDto>

    @HttpPOST(path = "parseuuid/{uuid}", description = "https://r3-cev.atlassian.net/browse/CORE-2404 coverage")
    fun parseUuid(@RestPathParameter uuid: String): UUID

    @HttpPOST(path = "stringMethodWithNameInAnnotation")
    fun stringMethodWithNameInAnnotation(@RestRequestBodyParameter(name = "correctName") incorrectName: String): String

    data class SomeTestNullableType(val number: Int, val str: String)
    data class ObjectWithNullableString(val str: String?)

    @HttpPOST(path = "apiReturningNullObject")
    fun apiReturningNullObject(): SomeTestNullableType?

    @HttpPOST(path = "apiReturningNullString")
    fun apiReturningNullString(): String?

    @HttpPOST(path = "apiReturningObjectWithNullableStringInside")
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

    @HttpGET(path = "laterAddedCall")
    @Suppress("MagicNumber")
    @RestSinceVersion(3)
    fun laterAddedCall(): String

    @HttpGET(path = "echoQuery", description = "Echoes what has been supplied in the query")
    fun echoQuery(@RestQueryParameter requestString: String): String

    @HttpGET(path = "echoPath/{requestString}", description = "Echoes what has been supplied in the path")
    fun echoPath(@RestPathParameter(name = "requestString", description = "The name") requestString: String): String

    @HttpWS(path = "counterFeed/{start}", responseDescription = "Given number supplied produces a WebSocket feed incrementing it")
    fun counterFeed(
        channel: DuplexChannel,
        @RestPathParameter start: Int,
        @RestQueryParameter(required = false) range: Int?
    )
}
