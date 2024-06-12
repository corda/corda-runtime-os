package net.corda.e2etest.utilities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.fail
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

/** "Private args" that are only exposed in here */
class AssertWithRetryArgs {
    var timeout: Duration = Duration.ofSeconds(10)
    var interval: Duration = Duration.ofMillis(500)
    var startDelay: Duration = Duration.ofMillis(10)
    lateinit var command: (() -> SimpleResponse)
    var condition: ((SimpleResponse) -> Boolean) = { it.code in 200..299 }
    var immediateFailCondition: ((SimpleResponse) -> Boolean) = { false }
    var failMessage: String = ""
}

object AssertWithRetryLogger {
    val logger: Logger = LoggerFactory.getLogger(AssertWithRetryLogger::class.java)
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

private data class Attempt(val attemptNumber: Int, val timeTried: Duration, val response: String)

private fun Iterable<Attempt>.prettyPrint(): String =
    joinToString("\n") { "${it.attemptNumber} (${it.timeTried}): ${it.response}" }

private fun <T> trackRetryAttempts(block: ((Attempt) -> Unit) -> T): T {
    val attempts = mutableListOf<Attempt>()
    try {
        return block { attempts.add(it) }
    } catch (t: Throwable) {
        fail("${t.message}\n\nAttempts:\n${attempts.prettyPrint()}", t)
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

    return trackRetryAttempts { addAttempt ->
        AssertWithRetryBuilder(args).apply(initialize).run {
            var response: SimpleResponse?

            var retry = 0
            var timeTried: Duration
            val startTime = Instant.now()
            Thread.sleep(args.startDelay.toMillis())
            do {
                response = args.command()
                if (retry == 0) {
                    println(response.url)
                }
                unbufferedPrint('.')
                if (args.immediateFailCondition(response)) {
                    fail("Failed without retry with status code = ${response.code} and body =\n${response.body}")
                }
                if (args.condition.invoke(response)) break
                retry++
                timeTried = Duration.between(startTime, Instant.now())
                addAttempt(Attempt(retry, timeTried, response.toString()))
                Thread.sleep(args.interval.toMillis())
            } while (timeTried < args.timeout)
            println()

            assertThat(args.condition.invoke(response!!))
                .withFailMessage(
                    "${args.failMessage}Retried ${response.url} and " +
                            "failed with status code = ${response.code} and body =\n${response.body}"
                )
                .isTrue

            response
        }
    }
}

/**
 * Same as [assertWithRetry], but exceptions thrown are also ignored during the retry process.
 *
 * This method should be preferred over [assertWithRetry] when the actual [AssertWithRetryArgs.command] might
 * receive ignorable transient exceptions during its execution that have nothing to do with the actual
 * [AssertWithRetryArgs.condition] that needs to be asserted.
 *
 * As an example, it might be used to wrap HTTP calls which could fail due to transient connectivity errors but
 * eventually succeed:
 *
 *      val response = assertWithRetryIgnoringExceptions {
 *          command { httpRequest(body) }
 *          condition { it.code == 200 }
 *      }
 */
@Suppress("ComplexMethod", "NestedBlockDepth")
fun assertWithRetryIgnoringExceptions(initialize: AssertWithRetryBuilder.() -> Unit): SimpleResponse {
    val args = AssertWithRetryArgs()

    return trackRetryAttempts { addAttempt ->
        AssertWithRetryBuilder(args).apply(initialize).run {
            var retry = 0
            var result: Any?
            var timeTried: Duration
            val startTime = Instant.now()

            Thread.sleep(args.startDelay.toMillis())
            do {
                result = try {
                    args.command()
                } catch (exception: Exception) {
                    AssertWithRetryLogger.logger
                        .info("CORE-20665: Caught exception during attempt $retry: $exception")
                    exception
                }

                AssertWithRetryLogger.logger.info("CORE-20665: Attempt $retry: $result")

                if (retry == 0 && result is SimpleResponse) {
                    println(result.url)
                }
                unbufferedPrint('.')

                retry++
                timeTried = Duration.between(startTime, Instant.now())

                AssertWithRetryLogger.logger.info("CORE-20665: Time elapsed so far: $timeTried")

                addAttempt(Attempt(retry, timeTried,
                        if (result is Exception) result.stackTraceToString() else result.toString()))

                if (result is SimpleResponse) {
                    if (args.immediateFailCondition(result)) {
                        fail("Failed without retry with status code = ${result.code} and body =\n${result.body}")
                    }
                    if (args.condition.invoke(result)) break
                }
                Thread.sleep(args.interval.toMillis())
            } while (timeTried < args.timeout)
            println()

            when (result) {
                is SimpleResponse -> {
                    assertThat(args.condition.invoke(result))
                        .withFailMessage(
                            "${args.failMessage}Retried ${result.url} and " +
                                    "failed with status code = ${result.code} and body =\n${result.body}"
                        )
                        .isTrue
                    AssertWithRetryLogger.logger.info("CORE-20665: Retried $retry times and failed with $result")
                    result
                }

                else -> {
                    AssertWithRetryLogger.logger.info("CORE-20665: Retried $retry times and failed with $result")
                    fail("${args.failMessage} Retried $retry times and failed with $result")
                }
            }
        }
    }
}

private fun unbufferedPrint(c: Char) {
    print(c)
    System.out.flush()
}
