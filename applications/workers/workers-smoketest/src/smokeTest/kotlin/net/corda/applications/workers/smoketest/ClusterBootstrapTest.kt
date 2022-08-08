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
    private val healthChecks = mapOf(
        "combined-worker" to System.getProperty("combinedWorkerHealthHttp"),
//        "crypto-worker" to System.getProperty("cryptoWorkerHealthHttp"),
        "db-worker" to System.getProperty("dbWorkerHealthHttp"),
        "flow-worker" to System.getProperty("flowWorkerHealthHttp"),
        "rpc-worker" to System.getProperty("rpcWorkerHealthHttp"),
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
                        if (isReady)
                            println("${it.key} is ready")
                        else
                            softly.fail("Problem with ${it.key} (${it.value}), \"status\"" +
                                    " returns: $isReady, body: ${lastResponse?.body()}")
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
                if (statusCode in 200..299)
                    return true
                else
                    println("Returned status $statusCode.")
            } catch (connectionException: IOException) {
                println("Cannot connect.")
            }
            Thread.sleep(1000)
        }
        return false
    }

    private fun sendAndReceiveResponse(name: String, endpoint: String): HttpResponse<String> {
        val url = "${endpoint}status"
        println("Checking $name on $url")
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }
}