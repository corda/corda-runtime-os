package net.corda.messaging.publisher

import net.corda.messaging.api.publisher.HttpRpcClient
import net.corda.metrics.CordaMetrics
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.time.Duration

class HttpRpcClientImpl(
    private val avroSchemaRegistry: AvroSchemaRegistry,
    private val httpClient: HttpClient = HttpClient.newBuilder().build(),
    private val requestBuilderFactory: () -> HttpRequest.Builder = {
        HttpRequest.newBuilder()
    },
    private val sleeper: (Long) -> Unit = {
        Thread.sleep(it)
    },
) : HttpRpcClient {

    private companion object {
        val logger = LoggerFactory.getLogger(HttpRpcClient::class.java)
        const val MAX_RETRIES = 3
        val TIMEOUT = Duration.ofSeconds(30)
        val INITIAL_DELAY = Duration.ofMillis(100)
        const val DELAY_INCREASE_FACTOR = 2L
        private const val SUCCESS = "SUCCESS"
        private const val FAILED = "FAILED"
    }

    override fun <T : Any, R : Any> send(uri: URI, requestBody: T, clz: Class<R>): R? {
        return try {
            val payload = avroSchemaRegistry.serialize(requestBody).array()
            val request = requestBuilderFactory()
                .uri(uri)
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build()
            val response = SendWithRetry(request).retry()
            response?.let {
                avroSchemaRegistry.deserialize(
                    it,
                    clz,
                    null,
                )
            }
        } catch (e: Exception) {
            throw CordaRuntimeException("Failed to send message to $uri", e)
        }
    }

    private inner class SendWithRetry(
        private val request: HttpRequest,
    ) {
        private val startTime = System.nanoTime()
        fun retry(): ByteBuffer? {
            return retry(MAX_RETRIES, INITIAL_DELAY)
        }
        private fun retry(
            retries: Int,
            nextDelay: Duration,
        ): ByteBuffer? {
            return try {
                sendMessage(request).let { (data, statusCode) ->
                    publishMetric(
                        success = true,
                        request = request,
                        responseSize = data?.size ?: 0,
                        statusCode = statusCode,
                    )
                    if ((data == null) || (data.isEmpty())) {
                        null
                    } else {
                        ByteBuffer.wrap(
                            data,
                        )
                    }
                }
            } catch (e: HttpRpcClient.HttpRpcException) {
                if (retries > 0) {
                    logger.info("Got error while sending HTTP request. Will retry again $retries times", e)
                    sleeper(nextDelay.toMillis())
                    return retry(retries - 1, nextDelay.multipliedBy(DELAY_INCREASE_FACTOR))
                } else {
                    publishMetric(
                        success = false,
                        request = request,
                        responseSize = e.responseSize,
                        statusCode = e.statusCode ?: -1,
                    )

                    throw e
                }
            }
        }

        @Suppress("ThrowsCount")
        private fun sendMessage(request: HttpRequest): Pair<ByteArray?, Int> {
            try {
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
                val statusCode = response.statusCode()
                if ((statusCode >= 200) && (statusCode < 300)) {
                    return response.body() to statusCode
                } else {
                    throw HttpRpcClient.HttpRpcException(response)
                }
            } catch (e: IOException) {
                throw HttpRpcClient.HttpRpcException(e)
            }
        }

        private fun publishMetric(
            success: Boolean,
            request: HttpRequest,
            responseSize: Int?,
            statusCode: Int,
        ) {
            val endTime = System.nanoTime()
            val operationStatus = if (success) {
                SUCCESS
            } else {
                FAILED
            }
            val uri = request.method() + request.uri().toString()
            CordaMetrics.Metric.Messaging.HTTPRPCResponseTime.builder()
                .withTag(CordaMetrics.Tag.OperationStatus, operationStatus)
                .withTag(CordaMetrics.Tag.HttpRequestUri, uri)
                .withTag(CordaMetrics.Tag.HttpResponseCode, statusCode.toString())
                .build()
                .record(Duration.ofNanos(endTime - startTime))

            if (responseSize != null) {
                CordaMetrics.Metric.Messaging.HTTPRPCResponseSize.builder()
                    .withTag(CordaMetrics.Tag.HttpRequestUri, uri)
                    .build()
                    .record(responseSize.toDouble())
            }
        }
    }
}
