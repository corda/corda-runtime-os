package net.corda.p2p.gateway.messaging.http

import io.netty.channel.nio.NioEventLoopGroup
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR
import net.corda.p2p.LinkInMessage
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.CommonHeader
import net.corda.p2p.crypto.MessageType
import net.corda.p2p.gateway.TestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File
import java.io.UncheckedIOException
import java.net.URI
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

/**
 * Before running the test:
 * * Compile the app:
 * ** `./gradlew :applications:gateway-app:clean :applications:gateway-app:appJar`
 * * Start kafka cluser (see libs/messaging/kafka-messaging-impl/src/kafka-integration-test/README.md):
 * ** For example: `docker-compose -f ./libs/messaging/kafka-messaging-impl/src/kafka-integration-test/kafka-docker/linux-friendly-kafka-cluster.yml up -d`
 */
class TestTheApp : TestBase() {
    private val sessionId = "session-1231"

    private fun getJvm(): String {
        return File(System.getProperty("java.home"))
            .resolve("bin")
            .resolve("java")
            .absolutePath
    }
    private fun getProjectRoot(file: File): String {
        if ((file.isDirectory) && (file.list()?.contains("applications") == true)) {
            return file.absolutePath
        }
        return getProjectRoot(file.parentFile)
    }
    private fun getProjectRoot(): String {
        val log4j = File(TestTheApp::class.java.classLoader.getResource("1mb.txt")!!.path)
        return getProjectRoot(log4j)
    }

    private var process: Process? = null

    fun startApp() {
        if (process != null) {
            return
        }
        val command = listOf(
            getJvm(),
            "-Djdk.net.hosts.file=${getProjectRoot()}/components/gateway/src/integration-test/resources/hosts",
            "-jar",
            "${getProjectRoot()}/applications/gateway-app/build/bin/corda-gateway-app-5.0.0.0-SNAPSHOT.jar",
            "--keyStore",
            "${getProjectRoot()}/components/gateway/src/integration-test/resources/sslkeystore_alice.jks",
            "--trustStore",
            "${getProjectRoot()}/components/gateway/src/integration-test/resources/truststore.jks",
            "--port",
            "3123",
            "--host",
            "www.alice.net",
        )

        println("Starting the application")
        val process = ProcessBuilder()
            .command(command)
            .start().also {
                this.process = it
            }

        reader()

        println("Process is running - pid: ${process.pid()}, waiting for server...")
        listening.join()
    }

    @AfterEach
    fun cleanUp() {
        process?.destroy()
        process?.waitFor()
        process = null
    }
    private val listening = CompletableFuture<Boolean>()
    private val gotError = CompletableFuture<Boolean>()

    private fun reader() {
        thread(isDaemon = true) {
            try {
                process!!.inputStream
                    .bufferedReader()
                    .lines()
                    .forEach {
                        if (it.contains("Gateway is running - HTTP server is www.alice.net:3123")) {
                            listening.complete(true)
                        } else if (it.contains("No mapping for session ($sessionId), discarding the message and returning an error."))
                            gotError.complete(true)
                    }
            } catch (e: UncheckedIOException) {
                gotError.completeExceptionally(e)
                listening.completeExceptionally(e)
            }
        }
    }

    @Test
    @Disabled
    fun `test the app`() {
        startApp()
        println("Server is ready, sending a message")

        val message = AuthenticatedDataMessage.newBuilder().apply {
            header = CommonHeader(MessageType.DATA, 0, sessionId, 1L, Instant.now().toEpochMilli())
            payload = ByteBuffer.wrap(ByteArray(0))
            authTag = ByteBuffer.wrap(ByteArray(0))
        }.build()
        val linkInMessage = LinkInMessage(message)
        val statusCode = CompletableFuture<HttpResponseStatus>()
        HttpClient(
            DestinationInfo(
                URI("http://www.alice.net:3123"),
                "www.alice.net",
                null
            ),
            aliceSslConfig,
            NioEventLoopGroup(1),
            NioEventLoopGroup(1),
            object : HttpEventListener {
                override fun onMessage(message: HttpMessage) {
                    statusCode.complete(message.statusCode)
                }
            }
        ).use { client ->
            client.start()
            client.write(linkInMessage.toByteBuffer().array())
            gotError.join()
            statusCode.join()
        }

        assertThat(statusCode).isCompletedWithValue(INTERNAL_SERVER_ERROR)
    }
}
