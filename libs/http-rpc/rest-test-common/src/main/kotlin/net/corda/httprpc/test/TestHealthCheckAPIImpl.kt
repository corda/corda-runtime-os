package net.corda.httprpc.test

import net.corda.httprpc.PluggableRestResource
import net.corda.httprpc.ws.DuplexChannel
import net.corda.lifecycle.Lifecycle
import net.corda.v5.base.util.debug
import org.slf4j.LoggerFactory
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
class TestHealthCheckAPIImpl : TestHealthCheckAPI, PluggableRestResource<TestHealthCheckAPI>, Lifecycle {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

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

    override fun counterFeed(channel: DuplexChannel, start: Int, range: Int?)  {

        var counter = start
        var scheduledFuture: ScheduledFuture<*>? = null

        channel.onConnect = {
            log.info("onConnect")
            scheduledFuture = scheduler.scheduleAtFixedRate(
                {
                    log.debug { "Sending: $counter" }
                    val future = channel.send("${counter++}")
                    if (range != null) {
                        if (counter >= start + range) {
                            // Wait for sent confirmation then close the channel
                            future.get()
                            channel.close()
                        }
                    }
                },
                10,
                10,
                TimeUnit.MILLISECONDS
            )
        }
        channel.onClose = { statusCode, reason ->
            log.info("Reacting to close event : $statusCode - $reason")
            scheduledFuture?.cancel(true)
        }
    }
}