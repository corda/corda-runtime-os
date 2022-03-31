package net.corda.applications.workers.smoketest.virtualnode.helpers

import org.assertj.core.api.Assertions.assertThat
import java.time.Duration

/** "Private args" that are only exposed in here */
class AssertWithRetryArgs {
    var timeout: Duration = Duration.ofSeconds(2)
    var command: (() -> SimpleResponse)? = null
    var condition: ((SimpleResponse) -> Boolean)? = null
    var failMessage: String = ""
}

/** The [assertWithRetry] builder class that helps implement the "DSL" */
class AssertWithRetryBuilder(private val args: AssertWithRetryArgs) {
    fun timeout(timeout: Duration) {
        args.timeout = timeout
    }

    fun command(command: () -> SimpleResponse) {
        args.command = command
    }

    fun condition(condition: (SimpleResponse) -> Boolean) {
        args.condition = condition
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
    var timeout = Duration.ofMillis(250)
    val args = AssertWithRetryArgs()

    AssertWithRetryBuilder(args).apply(initialize).run {
        var response: SimpleResponse?

        do {
            Thread.sleep(timeout.toMillis())
            timeout = timeout.multipliedBy(2)
            response = args.command!!.invoke()
            if (args.condition!!.invoke(response)) break
        } while (timeout.toMillis() < args.timeout.toMillis())

        assertThat(args.condition!!.invoke(response!!))
            .withFailMessage(
                "${args.failMessage}Retried ${response.url} and " +
                        "failed with status code = ${response.code} and body =\n${response.body}"
            )
            .isTrue

        return response
    }
}
