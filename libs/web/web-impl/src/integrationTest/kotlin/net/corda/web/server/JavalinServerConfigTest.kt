package net.corda.web.server

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.rest.ResponseCode
import net.corda.web.api.Endpoint
import net.corda.web.api.HTTPMethod
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.random.Random

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JavalinServerConfigTest {
    // real server, mock lifecycle bits
    private val lifecycleCoordinator = mock<LifecycleCoordinator>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) }.doReturn(lifecycleCoordinator)
    }
    private val path = "/aquaman"
    private val port = 9999
    private val url = "http://localhost:$port$path"
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    private val size = 100_000
    private val payload = Random.nextBytes(size)
    private val server = JavalinServer(lifecycleCoordinatorFactory, { JavalinServer.createJavalin(size.toLong()) }, mock())

    @BeforeAll
    fun setup() {
        server.registerEndpoint(Endpoint(HTTPMethod.POST, path, {
            it.status(ResponseCode.OK)
            it.result("Flood ${it.bodyAsBytes().size}")
            it
        }))
        server.start(port)
    }

    @AfterAll
    fun teardown() {
        server.stop()
    }

    @Test
    fun `accept large payload`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(ResponseCode.OK.statusCode)
        assertThat(response.body()).isEqualTo("Flood $size")
    }

    @Test
    fun `reject too large payload`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofByteArray(payload.plus(Random.nextBytes(1))))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(ResponseCode.CONTENT_TOO_LARGE.statusCode)
    }

    @Test
    fun `accept stream very large payload`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .headers("Content-Type", "application/octet-stream")
            .POST(HttpRequest.BodyPublishers.ofInputStream { payload.plus(Random.nextBytes(1)).inputStream() } )
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(ResponseCode.OK.statusCode)
        assertThat(response.body()).isEqualTo("Flood ${size + 1}")
    }

//    @Test
//    fun `start server for prometheus`() {
//        val endpoint = Endpoint(HTTPMethod.GET, "/metrics", { context ->
//            context.result(readMetricsFile())
//            context.header(Header.CACHE_CONTROL, "no-cache")
//            context
//        })
//        server.registerEndpoint(endpoint)
//
//        while(true) { }
//    }
//
//    private fun readMetricsFile(): String {
//        val expectedConfigYamlFile = this::class.java.classLoader.getResource("metrics2.txt")?.toURI()
//        return Files.readString(expectedConfigYamlFile?.toPath())
//    }
}