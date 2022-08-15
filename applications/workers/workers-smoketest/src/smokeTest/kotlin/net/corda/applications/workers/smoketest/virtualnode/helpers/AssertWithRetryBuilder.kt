package net.corda.applications.workers.smoketest.virtualnode.helpers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.fail
import java.time.Duration

/** "Private args" that are only exposed in here */
class AssertWithRetryArgs {
    var timeout: Duration = Duration.ofSeconds(10)
    var interval: Duration = Duration.ofMillis(250)
    var command: (() -> SimpleResponse)? = null
    var condition: ((SimpleResponse) -> Boolean)? = null
    var immediateFailCondition: ((SimpleResponse) -> Boolean)? = null
    var failMessage: String = ""
}

/** The [assertWithRetry] builder class that helps implement the "DSL" */
class AssertWithRetryBuilder(private val args: AssertWithRetryArgs) {
    fun timeout(timeout: Duration) {
        args.timeout = timeout
    }

    fun interval(duration: Duration) {
        args.interval = duration
    }

    fun command(command: () -> SimpleResponse) {
        args.command = command
    }

    fun condition(condition: (SimpleResponse) -> Boolean) {
        args.condition = condition
    }

    fun immediateFailCondition(condition: (SimpleResponse) -> Boolean) {
        args.immediateFailCondition = condition
    }

    fun failMessage(failMessage: String) {
        args.failMessage = failMessage + "\n"
    }
}

/**
 * Sort-of-DSL.  Asserts a command and retries if it doesn't initially succeed.
 *
 * Specify the [command] to execute, and the [condition] to assert, and optionally a [failMessage] and a different
 * [timeout]
 *
 *      val statusResponse = assertWithRetry {
 *          condition { it.code == 200 && it.toJson()["status"].textValue() == "OK" }
 *          command { cpiStatus(requestId) }
 *      }
 *
 * @throws [AssertionError] or similar if condition isn't successful.
 * @return [SimpleResponse] if successful
 */
fun assertWithRetry(initialize: AssertWithRetryBuilder.() -> Unit): SimpleResponse {
    val args = AssertWithRetryArgs()

    AssertWithRetryBuilder(args).apply(initialize).run {
        var response: SimpleResponse?

        var retry = 0
        var timeTried: Long
        do {
            Thread.sleep(args.interval.toMillis())
            response = args.command!!.invoke()
            if(null != args.immediateFailCondition && args.immediateFailCondition!!.invoke(response)) {
                fail("Failed without retry with status code = ${response.code} and body =\n${response.body}")
            }
            if (args.condition!!.invoke(response)) break
            retry++
            timeTried = args.interval.toMillis() * retry
            println("Failed after $retry retry ($timeTried ms): $response")
        } while (timeTried < args.timeout.toMillis())

        assertThat(args.condition!!.invoke(response!!))
            .withFailMessage(
                "${args.failMessage}Retried ${response.url} and " +
                        "failed with status code = ${response.code} and body =\n${response.body}"
            )
            .isTrue

        return response
    }
}
