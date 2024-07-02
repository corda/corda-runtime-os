package net.corda.sdk.rest

import net.corda.libs.configuration.exception.WrongConfigVersionException
import net.corda.rest.exception.ResourceAlreadyExistsException
import net.corda.sdk.network.OnboardFailedException
import net.corda.utilities.debug
import net.corda.utilities.trace
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object RestClientUtils {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val errOut: Logger = LoggerFactory.getLogger("SystemErr")

    private val maxWait: Duration = 10.seconds
    private val cooldownInterval: Duration = 2.seconds

    /**
     * Retry a given block of code until we time out
     * @param waitDuration the overall Duration to wait got before timing out, has default value
     * @param timeBetweenAttempts the Duration to wait between attempts at executing the block
     * @param operationName a description to use for logging
     * @param block the code you want to retry
     * @return if successful will return whatever the underlying block returns, otherwise will throw exception
     */
    @Suppress("ThrowsCount")
    fun <T> executeWithRetry(
        waitDuration: Duration = maxWait,
        timeBetweenAttempts: Duration = cooldownInterval,
        operationName: String,
        block: () -> T
    ): T {
        logger.trace { """Performing operation "$operationName"""" }
        val endTime = System.currentTimeMillis() + waitDuration.inWholeMilliseconds
        var lastException: Exception?
        do {
            try {
                return block()
            } catch (ex: Exception) {
                when (ex) {
                    // Allow an escape without retrying
                    is ResourceAlreadyExistsException,
                    is WrongConfigVersionException,
                    is OnboardFailedException -> throw ex
                    // All other exceptions, perform retry
                    else -> {
                        lastException = ex
                        logger.debug { """Cannot perform operation "$operationName" yet""" }
                        val remaining = (endTime - System.currentTimeMillis()).coerceAtLeast(0)
                        Thread.sleep(timeBetweenAttempts.inWholeMilliseconds.coerceAtMost(remaining))
                    }
                }
            }
        } while (System.currentTimeMillis() <= endTime)

        errOut.error("""Unable to perform operation "$operationName"""", lastException)
        throw lastException!!
    }
}
