package net.corda.httprpc.client

import net.corda.httprpc.client.config.HttpRpcClientConfig
import net.corda.httprpc.server.config.models.HttpRpcSSLSettings
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.impl.HttpRpcServerImpl
import net.corda.httprpc.ssl.impl.SslCertReadServiceStubImpl
import net.corda.httprpc.test.CustomSerializationAPI
import net.corda.httprpc.test.CustomSerializationAPIImpl
import net.corda.httprpc.test.CustomString
import net.corda.httprpc.test.TestHealthCheckAPI
import net.corda.httprpc.test.TestHealthCheckAPIImpl
import net.corda.httprpc.test.utls.findFreePort
import net.corda.v5.base.util.NetworkHostAndPort
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.file.Files

internal class HttpRpcSSLClientIntegrationTest : HttpRpcIntegrationTestBase() {
    companion object {
        private val port = findFreePort()

        private val sslService = SslCertReadServiceStubImpl {
            Files.createTempDirectory("HttpRpcSSLClientIntegrationTest")
        }

        @BeforeAll
        @JvmStatic
        fun setUpBeforeClass() {
            //System.setProperty("javax.net.debug", "all")
            val keyStoreInfo = sslService.getOrCreateKeyStore()
            val sslConfig = HttpRpcSSLSettings(keyStoreInfo.path, keyStoreInfo.password)
            val httpRpcSettings = HttpRpcSettings(
                NetworkHostAndPort("localhost", port),
                context,
                sslConfig,
                null,
                HttpRpcSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE
            )
            server = HttpRpcServerImpl(
                listOf(TestHealthCheckAPIImpl(), CustomSerializationAPIImpl()),
                securityManager,
                httpRpcSettings,
                true
            ).apply { start() }
        }

        @AfterAll
        @JvmStatic
        fun cleanUpAfterClass() {
            if (isServerInitialized()) {
                server.stop()
            }
            sslService.stop()
        }
    }

    @Test
    @Timeout(100)
    fun `start connection-aware client against server with accepted protocol version and SSL enabled succeeds`() {
        val client = HttpRpcClient(
            baseAddress = "https://localhost:$port/api/v1/",
            TestHealthCheckAPI::class.java,
            HttpRpcClientConfig()
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
                assertEquals(""""Pong for str = value"""", this.ping(TestHealthCheckAPI.PingPongData("value")))
                assertEquals(listOf(2.0, 3.0, 4.0), this.plusOne(listOf("1", "2", "3")))
                assertEquals(2L, this.plus(1L))
            }
        }
    }

    @Test
    @Timeout(100)
    fun `start client against server with accepted protocol version and SSL enabled and custom serializers succeeds`() {
        val client = HttpRpcClient(
            baseAddress = "https://localhost:$port/api/v1/",
            CustomSerializationAPI::class.java,
            HttpRpcClientConfig()
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
    fun `start client with SSL enabled against server with less than rpc version since but valid version `() {
        val client = HttpRpcClient(
            baseAddress = "https://localhost:$port/api/v1/",
            TestHealthCheckAPI::class.java,
            HttpRpcClientConfig()
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
                assertEquals(""""Pong for str = value"""", this.ping(TestHealthCheckAPI.PingPongData("value")))
                assertEquals(listOf(2.0, 3.0, 4.0), this.plusOne(listOf("1", "2", "3")))
                assertEquals(2L, this.plus(1L))
                assertThatThrownBy { this.laterAddedCall() }.isInstanceOf(UnsupportedOperationException::class.java)
            }
        }
    }

    @Test
    @Timeout(100)
    fun `start client with SSL enabled against server with lower protocol version than minimum expected fails`() {
        val client = HttpRpcClient(
            baseAddress = "https://localhost:$port/api/v1/",
            TestHealthCheckAPI::class.java,
            HttpRpcClientConfig()
                .enableSSL(true)
                .minimumServerProtocolVersion(3)
                .username(userAlice.username)
                .password(requireNotNull(userAlice.password))
        )

        assertThatThrownBy { client.start() }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
