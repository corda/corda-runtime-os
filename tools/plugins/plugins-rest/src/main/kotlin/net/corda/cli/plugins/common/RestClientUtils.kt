package net.corda.cli.plugins.common

import net.corda.rest.exception.ResourceAlreadyExistsException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

object RestClientUtils {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val errOut: Logger = LoggerFactory.getLogger("SystemErr")

    fun <T> executeWithRetry(
        waitDuration: Duration,
        operationName: String,
        onAlreadyExists: (ResourceAlreadyExistsException) -> T = ::reThrow,
        block: () -> T
    ): T {
        logger.info("""Performing operation "$operationName"""")
        val endTime = System.currentTimeMillis() + waitDuration.toMillis()
        var lastException: Exception?
        var sleep = 1000L
        do {
            try {
                return block()
            } catch (ex: ResourceAlreadyExistsException) {
                return onAlreadyExists(ex)
            } catch (ex: Exception) {
                lastException = ex
                logger.warn("""Cannot perform operation "$operationName" yet""")
                val remaining = (endTime - System.currentTimeMillis()).coerceAtLeast(0)
                Thread.sleep(sleep.coerceAtMost(remaining))
                sleep *= 2
            }
        } while (System.currentTimeMillis() <= endTime)

        errOut.error("""Unable to perform operation "$operationName"""", lastException)
        throw lastException!!
    }

    private fun reThrow(ex: ResourceAlreadyExistsException): Nothing {
        logger.info("Re-throwing", ex)
        throw ex
    }
}
