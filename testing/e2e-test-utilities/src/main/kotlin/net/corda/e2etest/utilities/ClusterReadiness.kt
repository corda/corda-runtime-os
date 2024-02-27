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
}

class ClusterReadinessChecker: ClusterReadiness {
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



    override fun assertIsReady(timeOut: Duration, sleepDuration: Duration) {
        runBlocking(Dispatchers.Default) {
            val softly = SoftAssertions()
            // check all workers are up and "ready"
            workerUrls
                .filter { !it.value.isNullOrBlank() }
                .map {
                    async {
                        var lastResponse: HttpResponse<String>? = null
                        val isReady: Boolean = tryUntil(timeOut, sleepDuration) {
                            sendAndReceiveResponse(it.key, it.value).also {
                                lastResponse = it
                            }
                        }
                        if (isReady) {
                            logger.info("${it.key} is ready")
                        }
                        else {
                            """Problem with ${it.key} (${it.value}), status returns not ready, 
                                | body: ${lastResponse?.body()}""".trimMargin().let {
                                logger.error(it)
                                softly.fail(it)
                            }
                        }
                    }
                }.awaitAll()

            softly.assertAll()
        }
    }

    private fun tryUntil(timeOut: Duration, sleepDuration: Duration, function: () -> HttpResponse<String>): Boolean {
        val startTime = Instant.now()
        while (Instant.now() < startTime.plusNanos(timeOut.toNanos())) {
            try {
                val response = function()
                val statusCode = response.statusCode()
                if (statusCode in 200..299) {
                    return true
                }
                else {
                    logger.info("Returned status $statusCode.")
                }
            } catch (connectionException: IOException) {
                logger.info("Cannot connect.", connectionException)
            }
            Thread.sleep(sleepDuration.toMillis())
        }
        return false
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