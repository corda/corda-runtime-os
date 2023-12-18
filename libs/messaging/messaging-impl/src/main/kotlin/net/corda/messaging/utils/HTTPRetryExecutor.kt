package net.corda.messaging.utils

import java.net.http.HttpResponse
import net.corda.messaging.api.exception.CordaHTTPClientErrorException
import net.corda.messaging.api.exception.CordaHTTPServerErrorException
import net.corda.utilities.trace
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class HTTPRetryExecutor {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        fun <T> withConfig(config: HTTPRetryConfig, block: () -> HttpResponse<T>): HttpResponse<T> {
            var currentDelay = config.initialDelay
            for (attempt in 0 until config.maxRetries) {
                val result = tryAttempt(attempt, config, block)
                if (result != null) return result

                log.trace { "Attempt #${attempt + 1} failed. Retrying in $currentDelay ms..." }
                Thread.sleep(currentDelay)
                currentDelay = (currentDelay * config.factor).toLong()
            }

            val errorMsg = "Retry logic exhausted all attempts without a valid return or rethrow, though this shouldn't be possible."
            log.trace { errorMsg }
            throw CordaRuntimeException(errorMsg)
        }

        private fun <T> tryAttempt(attempt: Int, config: HTTPRetryConfig, block: () -> HttpResponse<T>): HttpResponse<T>? {
            return try {
                log.trace { "HTTPRetryExecutor making attempt #${attempt + 1}." }
                val result = block()
                checkResponseStatus(result.statusCode())
                log.trace { "Operation successful after #${attempt + 1} attempt/s." }
                result
            } catch (e: Exception) {
                handleException(attempt, config, e)
                null
            }
        }

        private fun handleException(attempt: Int, config: HTTPRetryConfig, e: Exception) {
            val isFinalAttempt = attempt == config.maxRetries - 1
            val isRetryable = config.retryOn.any { it.isInstance(e) }

            if (!isRetryable || isFinalAttempt) {
                val errorMsg = when {
                    isFinalAttempt -> "Operation failed after ${config.maxRetries} attempts."
                    else -> "HTTPRetryExecutor caught a non-retryable exception: ${e.message}"
                }
                log.trace { errorMsg }
                throw e
            }
        }

        private fun checkResponseStatus(statusCode: Int) {
            log.trace { "Received response with status code $statusCode" }
            when (statusCode) {
                in 400..499 -> throw CordaHTTPClientErrorException(statusCode, "Server returned status code $statusCode.")
                in 500..599 -> throw CordaHTTPServerErrorException(statusCode, "Server returned status code $statusCode.")
            }
        }
    }
}
