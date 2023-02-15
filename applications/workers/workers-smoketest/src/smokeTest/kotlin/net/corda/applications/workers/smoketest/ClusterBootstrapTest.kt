package net.corda.applications.workers.smoketest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

@Order(2)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ClusterBootstrapTest {

    private companion object {
        private val logger = LoggerFactory.getLogger(ClusterBootstrapTest::class.java)
    }

    private val healthChecks = mapOf(
        "combined-worker" to System.getProperty("combinedWorkerHealthHttp"),
//        "crypto-worker" to System.getProperty("cryptoWorkerHealthHttp"),
        "db-worker" to System.getProperty("dbWorkerHealthHttp"),
        "flow-worker" to System.getProperty("flowWorkerHealthHttp"),
        "rest-worker" to System.getProperty("restWorkerHealthHttp"),
    )
    private val client = HttpClient.newBuilder().build()

    @Test
    fun checkCluster() {
        runBlocking(Dispatchers.Default) {
            val softly = SoftAssertions()
            // check all workers are up and "ready"
            healthChecks
                .filter { !it.value.isNullOrBlank() }
                .map {
                    async {
                        var lastResponse: HttpResponse<String>? = null
                        val isReady: Boolean = tryUntil(Duration.ofSeconds(120)) {
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

    private fun tryUntil(timeOut: Duration, function: () -> HttpResponse<String>): Boolean {
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
            Thread.sleep(1000)
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