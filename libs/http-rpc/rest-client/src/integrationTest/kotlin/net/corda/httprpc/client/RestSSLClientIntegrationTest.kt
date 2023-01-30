package net.corda.httprpc.client

import net.corda.httprpc.client.config.RestClientConfig
import net.corda.httprpc.server.config.models.RestSSLSettings
import net.corda.httprpc.server.config.models.RestServerSettings
import net.corda.httprpc.server.impl.RestServerImpl
import net.corda.httprpc.ssl.impl.SslCertReadServiceStubImpl
import net.corda.httprpc.test.CustomSerializationAPI
import net.corda.httprpc.test.CustomSerializationAPIImpl
import net.corda.httprpc.test.CustomString
import net.corda.httprpc.test.TestHealthCheckAPI
import net.corda.httprpc.test.TestHealthCheckAPIImpl
import net.corda.httprpc.test.utils.multipartDir
import net.corda.utilities.NetworkHostAndPort
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.file.Files

internal class RestSSLClientIntegrationTest : RestIntegrationTestBase() {
    companion object {

        private val sslService = SslCertReadServiceStubImpl {
            Files.createTempDirectory("RestSSLClientIntegrationTest")
        }

        @BeforeAll
        @JvmStatic
        @Suppress("unused")
        fun setUpBeforeClass() {
            //System.setProperty("javax.net.debug", "all")
            val keyStoreInfo = sslService.getOrCreateKeyStore()
            val sslConfig = RestSSLSettings(keyStoreInfo.path, keyStoreInfo.password)
            val restServerSettings = RestServerSettings(
                NetworkHostAndPort("localhost", 0),
                context,
                sslConfig,
                null,
                RestServerSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE,
                20000L
            )
            server = RestServerImpl(
                listOf(TestHealthCheckAPIImpl(), CustomSerializationAPIImpl()),
                ::securityManager,
                restServerSettings,
                multipartDir,
                true
            ).apply { start() }
        }

        @AfterAll
        @JvmStatic
        @Suppress("unused")
        fun cleanUpAfterClass() {
            if (isServerInitialized()) {
                server.close()
            }
            sslService.stop()
        }
    }

    @Test
    @Timeout(100)
    fun `start connection-aware client against server with accepted protocol version and SSL enabled succeeds`() {
        val client = RestClient(
            baseAddress = "https://localhost:${server.port}/api/v1/",
            TestHealthCheckAPI::class.java,
            RestClientConfig()
                .enableSSL(true)
                .minimumServerProtocolVersion(1)
                .username(userAlice.username)
                .password(requireNotNull(userAlice.password))
        )

        client.use {
            val connection = client.start()
            with (connection.proxy) {
                assertEquals(3, this.plus(2L))
                assertEquals(Unit::class.java, this.voidResponse()::class.java)
                assertEquals("Pong for str = value", this.ping(TestHealthCheckAPI.PingPongData("value")))
                assertEquals(listOf(2.0, 3.0, 4.0), this.plusOne(listOf("1", "2", "3")))
                assertEquals(2L, this.plus(1L))
            }
        }
    }

    @Test
    @Timeout(100)
    fun `start client against server with accepted protocol version and SSL enabled and custom serializers succeeds`() {
        val client = RestClient(
            baseAddress = "https://localhost:${server.port}/api/v1/",
            CustomSerializationAPI::class.java,
            RestClientConfig()
                .enableSSL(true)
                .minimumServerProtocolVersion(1)
                .username(userAlice.username)
                .password(requireNotNull(userAlice.password))
        )

        client.use {
            val connection = client.start()
            with (connection.proxy) {
                assertEquals("custom custom test", this.printString(CustomString("test")).s)
            }
        }
    }

    @Test
    @Timeout(100)
    fun `start client with SSL enabled against server with less than rest version since but valid version `() {
        val client = RestClient(
            baseAddress = "https://localhost:${server.port}/api/v1/",
            TestHealthCheckAPI::class.java,
            RestClientConfig()
                .enableSSL(true)
                .minimumServerProtocolVersion(1)
                .username(userAlice.username)
                .password(requireNotNull(userAlice.password))
        )

        client.use {
            val connection = client.start()
            with (connection.proxy) {
                assertEquals(3, this.plus(2L))
                assertEquals(Unit::class.java, this.voidResponse()::class.java)
                assertEquals("Pong for str = value", this.ping(TestHealthCheckAPI.PingPongData("value")))
                assertEquals(listOf(2.0, 3.0, 4.0), this.plusOne(listOf("1", "2", "3")))
                assertEquals(2L, this.plus(1L))
                assertThatThrownBy { this.laterAddedCall() }.isInstanceOf(UnsupportedOperationException::class.java)
            }
        }
    }

    @Test
    @Timeout(100)
    fun `start client with SSL enabled against server with lower protocol version than minimum expected fails`() {
        val client = RestClient(
            baseAddress = "https://localhost:${server.port}/api/v1/",
            TestHealthCheckAPI::class.java,
            RestClientConfig()
                .enableSSL(true)
                .minimumServerProtocolVersion(3)
                .username(userAlice.username)
                .password(requireNotNull(userAlice.password))
        )

        assertThatThrownBy { client.start() }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
