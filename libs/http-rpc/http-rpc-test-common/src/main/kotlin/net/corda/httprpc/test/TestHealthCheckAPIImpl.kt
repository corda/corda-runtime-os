package net.corda.httprpc.test

import net.corda.httprpc.PluggableRPCOps
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Calendar.DAY_OF_YEAR
import java.util.GregorianCalendar
import java.util.UUID

@Suppress("TooManyFunctions")
class TestHealthCheckAPIImpl : TestHealthCheckAPI, PluggableRPCOps<TestHealthCheckAPI> {

    override val targetInterface: Class<TestHealthCheckAPI>
        get() = TestHealthCheckAPI::class.java

    override val protocolVersion: Int
        get() = 2

    override fun void() = "Sane"

    override fun voidResponse() {}

    override fun hello(pathParam: String, param: Int?) = "Hello $param : $pathParam"

    override fun hello2(queryParam: String?, pathParam: String) = "Hello queryParam: $queryParam, pathParam : $pathParam"

    override fun ping(pingPongData: TestHealthCheckAPI.PingPongData?) = "Pong for ${pingPongData?.str}"

    override fun plusOne(numbers: List<String>) = numbers.map { it.toDouble() + 1.0 }

    override fun plus(number: Long): Long = number + 1

    override fun plusDouble(number: Double): Double = number + 1.0

    override fun bodyPlayground(s1: String?, s2: String?) = "$s1 $s2"

    override fun timeCall(time: TestHealthCheckAPI.TimeCallDto): String = time.time.toString()

    override fun dateCall(date: TestHealthCheckAPI.DateCallDto): String =
        DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.ofInstant(date.date.toInstant(), ZoneId.of("UTC")))

    override fun instantCall(instant: TestHealthCheckAPI.InstantCallDto): String = instant.instant.toString()

    override fun throwException(exception: String) {

        throw Class.forName(exception)
            .getDeclaredConstructor(String::class.java)
            .newInstance("$exception thrown from throwException method") as Throwable
    }

    override fun laterAddedCall(): String = "Not implemented in protocol version 2"

    override fun firstDaysOfTheYear(year: Int, daysCount: Int): List<TestHealthCheckAPI.DateCallDto> {
        val calendar = GregorianCalendar().apply {
            set(Calendar.YEAR, year)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return (1..daysCount).map { TestHealthCheckAPI.DateCallDto(calendar.apply { set(DAY_OF_YEAR, it) }.time) }
    }

    override fun parseUuid(uuid: String): UUID = UUID.fromString(uuid)

    override fun echoQuery(requestString: String): String {
        return requestString
    }

    override fun echoPath(requestString: String): String {
        return requestString
    }
}