package net.corda.p2p.gateway.messaging

import com.typesafe.config.Config
import net.corda.v5.base.util.hours
import net.corda.v5.base.util.minutes
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class GatewayConfigurationTest {
    @Test
    fun `toGatewayConfiguration return correct configuration with default connectionConfig`() {
        val sslConfig = mock<Config> {
            on { getEnum(RevocationConfigMode::class.java, "revocationCheck.mode") } doReturn RevocationConfigMode.HARD_FAIL
            on { getEnum(TlsType::class.java, "tlsType") } doReturn TlsType.ONE_WAY
        }
        val config = mock<Config> {
            on { hasPath("connectionConfig") } doReturn false
            on { getInt("hostPort") } doReturn 231
            on { getString("urlPath") } doReturn "/"
            on { getString("hostAddress") } doReturn "address"
            on { getConfig("sslConfig") } doReturn sslConfig
            on { getLong("maxRequestSize") } doReturn 1_000
            on { getBoolean("traceLogging") } doReturn false
        }

        val gatewayConfig = config.toGatewayConfiguration()

        assertThat(gatewayConfig).isEqualTo(
            GatewayConfiguration(
                hostPort = 231,
                urlPath = "/",
                hostAddress = "address",
                connectionConfig = ConnectionConfiguration(),
                maxRequestSize = 1_000,
                sslConfig = SslConfiguration(
                    revocationCheck = RevocationConfig(RevocationConfigMode.HARD_FAIL),
                    tlsType = TlsType.ONE_WAY
                )
            )
        )
    }

    @Test
    fun `toGatewayConfiguration return correct configuration without default connectionConfig`() {
        val connectionConfiguration = mock<Config> {
            on { getLong("maxClientConnections") } doReturn 100
            on { getLong("acquireTimeout") } doReturn 300
            on { getLong("connectionIdleTimeout") } doReturn 36000
            on { getLong("responseTimeout") } doReturn 20000
            on { getLong("retryDelay") } doReturn 21 * 60000
            on { getLong("maxReconnectionDelay") } doReturn 900
            on { getLong("initialReconnectionDelay") } doReturn 1
        }
        val sslConfig = mock<Config> {
            on { getEnum(RevocationConfigMode::class.java, "revocationCheck.mode") } doReturn RevocationConfigMode.HARD_FAIL
            on { getEnum(TlsType::class.java, "tlsType") } doReturn TlsType.ONE_WAY
        }
        val config = mock<Config> {
            on { hasPath("connectionConfig") } doReturn true
            on { getInt("hostPort") } doReturn 231
            on { getString("hostAddress") } doReturn "address"
            on { getString("urlPath") } doReturn "/"
            on { getConfig("sslConfig") } doReturn sslConfig
            on { getLong("maxRequestSize") } doReturn 1_000
            on { getBoolean("traceLogging") } doReturn false
            on { getConfig("connectionConfig") } doReturn connectionConfiguration
        }

        val gatewayConfig = config.toGatewayConfiguration()

        assertThat(gatewayConfig).isEqualTo(
            GatewayConfiguration(
                hostPort = 231,
                urlPath = "/",
                hostAddress = "address",
                connectionConfig = ConnectionConfiguration(
                    maxClientConnections = 100,
                    acquireTimeout = 5.minutes,
                    connectionIdleTimeout = 10.hours,
                    responseTimeout = 20.seconds,
                    retryDelay = 21.minutes,
                    initialReconnectionDelay = 1.seconds,
                    maxReconnectionDelay = 15.minutes,
                ),
                maxRequestSize = 1_000,
                sslConfig = SslConfiguration(
                    revocationCheck = RevocationConfig(RevocationConfigMode.HARD_FAIL),
                    tlsType = TlsType.ONE_WAY
                )
            )
        )
    }
}
