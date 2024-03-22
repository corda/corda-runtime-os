package net.corda.messaging.utils

import net.corda.messaging.api.exception.CordaHTTPClientErrorException
import net.corda.messaging.api.exception.CordaHTTPClientSideTransientException
import net.corda.messaging.api.exception.CordaHTTPServerErrorException
import net.corda.metrics.CordaMetrics
import net.corda.utilities.trace
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.http.HttpResponse
import java.time.Duration

class HTTPRetryExecutor {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val SUCCESS: String = "SUCCESS"
        private const val FAILED: String = "FAILED"

        fun withConfig(config: HTTPRetryConfig, block: () -> HttpResponse<ByteArray>): HttpResponse<ByteArray> {
            var currentDelay = config.initialDelay
            for (i in 0 until config.times) {
                val result = tryAttempt(i, config, block)
                if (result != null) return result

                log.trace { "Attempt #${i + 1} failed. Retrying in $currentDelay ms..." }
                Thread.sleep(currentDelay)
                currentDelay = (currentDelay * config.factor).toLong()
            }

            val errorMsg = "Retry logic exhausted all attempts without a valid return or rethrow, though this shouldn't be possible."
            log.trace { errorMsg }
            throw CordaRuntimeException(errorMsg)
        }

        private fun tryAttempt(
            i: Int,
            config: HTTPRetryConfig,
            block: () -> HttpResponse<ByteArray>
        ): HttpResponse<ByteArray>? {
            val startTime = System.nanoTime()

            return try {
                log.trace { "HTTPRetryExecutor making attempt #${i + 1}." }
                val result = block()
                checkResponseStatus(result.statusCode())
                buildMetricForResponse(config, startTime, SUCCESS, result)
                log.trace { "Operation successful after #${i + 1} attempt/s." }
                result
            } catch (e: Exception) {
                buildMetricForResponse(config, startTime, FAILED)
                handleException(i, config, e)
                null
            }
        }

        private fun handleException(attempt: Int, config: HTTPRetryConfig, e: Exception) {
            val isFinalAttempt = attempt == config.times - 1
            val isRetryable = config.retryOn.any { it.isAssignableFrom(e::class.java) }

            if (!isRetryable || isFinalAttempt) {
                val errorMsg = when {
                    isFinalAttempt -> "Operation failed after ${config.times} attempts."
                    else -> "HTTPRetryExecutor caught a non-retryable exception: ${e.message}"
                }
                log.trace { errorMsg }
                throw e
            }
        }

        @Suppress("ThrowsCount")
        private fun checkResponseStatus(statusCode: Int) {
            log.trace { "Received response with status code $statusCode" }
            when (statusCode) {
                503 -> throw CordaHTTPClientSideTransientException(statusCode, "Server returned a transient error")
                in 400..499 -> throw CordaHTTPClientErrorException(statusCode, "Server returned status code $statusCode.")
                in 500..599 -> throw CordaHTTPServerErrorException(statusCode, "Server returned status code $statusCode.")
            }
        }

        private fun buildMetricForResponse(
            config: HTTPRetryConfig,
            startTime: Long,
            operationStatus: String,
            response: HttpResponse<ByteArray>? = null
        ) {
            val endTime = System.nanoTime()
            recordResponseTimeMetric(config, startTime, endTime, operationStatus, response)
            response?.let { recordResponseSizeMetric(config, response) }
        }

        private fun recordResponseTimeMetric(
            config: HTTPRetryConfig,
            startTime: Long,
            endTime: Long,
            operationStatus: String,
            response: HttpResponse<ByteArray>?
        ) {
            CordaMetrics.Metric.Messaging.HTTPRPCResponseTime.builder()
                .withTag(CordaMetrics.Tag.OperationStatus, operationStatus)
                .withTag(CordaMetrics.Tag.HttpResponseCode, response?.statusCode().toString())
                .apply { config.additionalMetrics.forEach { (tag, value) -> withTag(tag, value) } }
                .build()
                .record(Duration.ofNanos(endTime - startTime))
        }

        private fun recordResponseSizeMetric(
            config: HTTPRetryConfig,
            response: HttpResponse<ByteArray>
        ) {
            CordaMetrics.Metric.Messaging.HTTPRPCResponseSize.builder()
                .apply { config.additionalMetrics.forEach { (tag, value) -> withTag(tag, value) } }
                .build()
                .record(response.body().size.toDouble())
        }
    }
}
