package net.corda.gradle.plugin

import java.net.ConnectException
import java.net.Socket
import java.time.Duration
import java.time.Instant

/**
 * Sleeps the thread for the specified time period.
 * @param millis number of milliseconds to sleep the thread for.
 */
fun rpcWait(millis: Long = 1000) {
    try {
        Thread.sleep(millis)
    } catch (e: InterruptedException) {
        throw UnsupportedOperationException("Interrupts not supported.", e)
    }
}

/**
 * Checks if the given port is in use.
 * @return true if the port is in use, false otherwise.
 */
fun isPortInUse(host: String, port: Int): Boolean {
    return try {
        Socket(host, port)
        true
    } catch (_: ConnectException) {
        false
    }
}

/**
 * Automatically retries the [block] until the operation is successful or [timeout] is reached.
 * @param timeout time to wait for the operation to complete. Default value is 10 seconds.
 * @param cooldown time to wait between retries. Default value is 1 second.
 * @param block the block of code to execute
 * @throws Exception if the operation fails after all retries
 */
fun <R> retry(
    timeout: Duration = Duration.ofMillis(10000),
    cooldown: Duration = Duration.ofMillis(1000),
    block: () -> R
): R {

    var firstException: Exception? = null
    val start = Instant.now()
    var elapsed = Duration.between(start, Instant.now())

    while (elapsed < timeout) {
        try {
            return block()
        } catch (e: Exception) {
            if (firstException == null) {
                firstException = e
            }
            rpcWait(cooldown.toMillis())
            elapsed = Duration.between(start, Instant.now())
        }
    }; throw firstException!!
}

/**
 * Automatically retries the [block] until the operation is successful or max [attempts] are reached.
 * @param attempts a positive number of attempts to retry the operation if fails. Default value is 10 attempts.
 * @param cooldown time to wait between retries. Default value is 1 second.
 * @param block the block of code to execute
 * @throws Exception if the operation fails after all retries
 */
fun <R> retryAttempts(
    attempts: Int = 10,
    cooldown: Duration = Duration.ofMillis(1000),
    block: () -> R
): R {
    require(attempts > 0) { "Number of attempts should be positive" }
    var firstException: Exception? = null

    for (attempt in (1..attempts)) {
        try {
            return block()
        } catch (e: Exception) {
            if (firstException == null) {
                firstException = e
            }
            rpcWait(cooldown.toMillis())
        }
    }; throw firstException!!
}
