package net.corda.e2etest.utilities

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.SoftAssertions
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

interface ClusterReadiness {
    fun assertIsReady(
        timeOut: Duration = Duration.ofSeconds(120),
        sleepDuration: Duration = Duration.ofSeconds(1)
    )

    fun remainsReady(
        timeOut: Duration = Duration.ofSeconds(30),
        sleepDuration: Duration = Duration.ofSeconds(1)
    )
}

class ClusterReadinessChecker : ClusterReadiness {
    private companion object {
        private val logger = LoggerFactory.getLogger(ClusterReadiness::class.java)
        private val workerUrls = mapOf(
            "crypto-worker" to System.getProperty("cryptoWorkerUrl"),
            "db-worker" to System.getProperty("dbWorkerUrl"),
            "flow-worker" to System.getProperty("flowWorkerUrl"),
            "flow-mapper-worker" to System.getProperty("flowMapperWorkerUrl"),
            "verification-worker" to System.getProperty("verificationWorkerUrl"),
            "persistence-worker" to System.getProperty("persistenceWorkerUrl"),
            "token-selection-worker" to System.getProperty("tokenSelectionWorkerUrl"),
            "rest-worker" to System.getProperty("restWorkerUrl"),
            "uniqueness-worker" to System.getProperty("uniquenessWorkerUrl"),
        )
    }

    private val client = HttpClient.newBuilder().build()

    private fun concurrentlyPollAllWorkers(
        timeOut: Duration, sleepDuration: Duration,
        block: (
            timeOut: Duration, sleepDuration: Duration,
            workerUrl: Map.Entry<String, String>, softAssertions: SoftAssertions
        ) -> Unit
    ) {
        runBlocking(Dispatchers.Default) {
            val softly = SoftAssertions()
            // Execute `block` supplied concurrently on all the workers
            workerUrls
                .filter { !it.value.isNullOrBlank() }
                .map { workerUrl ->
                    async {
                        block(timeOut, sleepDuration, workerUrl, softly)
                    }
                }.awaitAll()

            softly.assertAll()
        }
    }

    override fun assertIsReady(timeOut: Duration, sleepDuration: Duration) {
        concurrentlyPollAllWorkers(timeOut, sleepDuration, ::checkWorkerReady)
    }

    private fun checkWorkerReady(
        timeOut: Duration,
        sleepDuration: Duration,
        it: Map.Entry<String, String>,
        softly: SoftAssertions
    ) {
        var lastResponse: HttpResponse<String>? = null
        val isReady: Boolean = tryUntilSuccess(timeOut, sleepDuration) {
            sendAndReceiveResponse(it.key, it.value).also {
                lastResponse = it
            }
        }
        if (isReady) {
            logger.info("${it.key} is ready")
        } else {
            """Problem with ${it.key} (${it.value}), status returns not ready, 
                                    | body: ${lastResponse?.body()}""".trimMargin().let {
                logger.error(it)
                softly.fail(it)
            }
        }
    }

    override fun remainsReady(timeOut: Duration, sleepDuration: Duration) {
        concurrentlyPollAllWorkers(timeOut, sleepDuration, ::checkWorkerRemainsReady)
    }

    private fun checkWorkerRemainsReady(
        timeOut: Duration,
        sleepDuration: Duration,
        workerUrl: Map.Entry<String, String>,
        softAssertions: SoftAssertions
    ) {
        var lastResponse: HttpResponse<String>? = null
        val lastReady: Boolean = tryContinuously(timeOut, sleepDuration) {
            sendAndReceiveResponse(workerUrl.key, workerUrl.value).also {
                lastResponse = it
            }
        }
        if (lastReady) {
            logger.info("${workerUrl.key} is ready and stable")
        } else {
            """Problem with ${workerUrl.key} (${workerUrl.value}), status returns not ready, 
                                    | body: ${lastResponse?.body()}""".trimMargin().let {
                logger.error(it)
                softAssertions.fail(it)
            }
        }
    }

    private fun tryUntilSuccess(timeOut: Duration, sleepDuration: Duration, function: () -> HttpResponse<String>): Boolean {
        val startTime = Instant.now()
        while (Instant.now() < startTime.plusNanos(timeOut.toNanos())) {
            try {
                val response = function()
                val statusCode = response.statusCode()
                if (statusCode in 200..299) {
                    return true
                } else {
                    logger.info("Returned status $statusCode.")
                }
            } catch (connectionException: IOException) {
                logger.info("Cannot connect.", connectionException)
            }
            Thread.sleep(sleepDuration.toMillis())
        }
        return false
    }

    private fun tryContinuously(
        timeOut: Duration,
        sleepDuration: Duration,
        function: () -> HttpResponse<String>
    ): Boolean {
        val startTime = Instant.now()
        var lastSuccess = false
        while (Instant.now() < startTime.plusNanos(timeOut.toNanos())) {
            try {
                val response = function()
                val statusCode = response.statusCode()
                if (statusCode in 200..299) {
                    lastSuccess = true
                } else {
                    logger.info("Returned status during continuous polling: $statusCode.")
                    lastSuccess = false
                }
            } catch (connectionException: IOException) {
                logger.info("Cannot connect.", connectionException)
                lastSuccess = false
            }
            Thread.sleep(sleepDuration.toMillis())
        }
        return lastSuccess
    }

    private fun sendAndReceiveResponse(name: String, endpoint: String): HttpResponse<String> {
        val url = "${endpoint}status"
        logger.info("Checking $name on $url")
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }
}