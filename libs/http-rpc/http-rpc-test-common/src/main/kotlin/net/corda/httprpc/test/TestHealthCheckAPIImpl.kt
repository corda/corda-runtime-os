package net.corda.httprpc.test

import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.ws.DuplexChannel
import net.corda.httprpc.ws.DuplexChannelCloseContext
import net.corda.httprpc.ws.DuplexConnectContext
import net.corda.httprpc.ws.impl.DefaultDuplexChannel
import net.corda.lifecycle.Lifecycle
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Calendar.DAY_OF_YEAR
import java.util.GregorianCalendar
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Suppress("TooManyFunctions")
class TestHealthCheckAPIImpl : TestHealthCheckAPI, PluggableRPCOps<TestHealthCheckAPI>, Lifecycle {

    override val targetInterface: Class<TestHealthCheckAPI>
        get() = TestHealthCheckAPI::class.java

    override val protocolVersion: Int
        get() = 2

    private val scheduler = Executors.newScheduledThreadPool(1)

    override val isRunning: Boolean
        get() = !scheduler.isShutdown

    override fun start() {
    }

    override fun stop() {
        scheduler.shutdown()
    }

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

    override fun stringMethodWithNameInAnnotation(incorrectName: String): String = "Completed $incorrectName"

    override fun apiReturningNullObject(): TestHealthCheckAPI.SomeTestNullableType? {
        return null
    }

    override fun apiReturningNullString(): String? {
        return null
    }

    override fun apiReturningObjectWithNullableStringInside(): TestHealthCheckAPI.ObjectWithNullableString {
        return TestHealthCheckAPI.ObjectWithNullableString(null)
    }

    override fun echoQuery(requestString: String): String {
        return requestString
    }

    override fun echoPath(requestString: String): String {
        return requestString
    }

    override fun counterFeed(): DuplexChannel {

        var counter = 0
        var scheduledFuture: ScheduledFuture<*>? = null

        return object : DefaultDuplexChannel() {
            override fun onConnect(connectContext: DuplexConnectContext) {
                super.onConnect(connectContext)

                scheduledFuture = scheduler.scheduleAtFixedRate(
                    { connectContext.send("${counter++}") },
                    10,
                    10,
                    TimeUnit.MILLISECONDS
                )
            }

            override fun onClose(context: DuplexChannelCloseContext) {
                scheduledFuture?.cancel(true)
                super.onClose(context)
            }

            override fun close() {
                scheduledFuture?.cancel(true)
                super.close()
            }
        }
    }
}