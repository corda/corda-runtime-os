package net.corda.p2p.gateway.messaging

import com.typesafe.config.Config
import net.corda.v5.base.util.hours
import net.corda.v5.base.util.minutes
import net.corda.v5.base.util.seconds
import net.corda.v5.base.util.toBase64
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class GatewayConfigurationTest {
    @Test
    fun `toGatewayConfiguration return correct configuration with default connectionConfig`() {
        val sslConfig = mock<Config> {
            on { getEnum(RevocationConfigMode::class.java, "revocationCheck.mode") } doReturn RevocationConfigMode.HARD_FAIL
            on { getString("keyStore") } doReturn byteArrayOf(1, 2, 3).toBase64()
            on { getString("keyStorePassword") } doReturn "pass1"
            on { getString("trustStore") } doReturn byteArrayOf(4, 5).toBase64()
            on { getString("trustStorePassword") } doReturn "pass2"
        }
        val config = mock<Config> {
            on { hasPath("connectionConfig") } doReturn false
            on { getInt("hostPort") } doReturn 231
            on { getString("hostAddress") } doReturn "address"
            on { getConfig("sslConfig") } doReturn sslConfig
            on { getBoolean("traceLogging") } doReturn false
        }

        val gatewayConfig = config.toGatewayConfiguration()

        assertThat(gatewayConfig).isEqualTo(
            GatewayConfiguration(
                hostPort = 231,
                hostAddress = "address",
                connectionConfig = ConnectionConfiguration(),
                sslConfig = SslConfiguration(
                    rawTrustStore = byteArrayOf(4, 5),
                    rawKeyStore = byteArrayOf(1, 2, 3),
                    keyStorePassword = "pass1",
                    trustStorePassword = "pass2",
                    revocationCheck =
                    RevocationConfig(RevocationConfigMode.HARD_FAIL)
                )
            )
        )
    }

    @Test
    fun `toGatewayConfiguration return correct configuration without default connectionConfig`() {
        val connectionConfiguration = mock<Config> {
            on { getLong("maxClientConnections") } doReturn 100
            on { getDuration("acquireTimeout") } doReturn 5.minutes
            on { getDuration("connectionIdleTimeout") } doReturn 10.hours
            on { getDuration("responseTimeout") } doReturn 20.seconds
            on { getDuration("retryDelay") } doReturn 21.minutes
        }
        val sslConfig = mock<Config> {
            on { getEnum(RevocationConfigMode::class.java, "revocationCheck.mode") } doReturn RevocationConfigMode.HARD_FAIL
            on { getString("keyStore") } doReturn byteArrayOf(1, 2, 3).toBase64()
            on { getString("keyStorePassword") } doReturn "pass1"
            on { getString("trustStore") } doReturn byteArrayOf(4, 5).toBase64()
            on { getString("trustStorePassword") } doReturn "pass2"
        }
        val config = mock<Config> {
            on { hasPath("connectionConfig") } doReturn true
            on { getInt("hostPort") } doReturn 231
            on { getString("hostAddress") } doReturn "address"
            on { getConfig("sslConfig") } doReturn sslConfig
            on { getBoolean("traceLogging") } doReturn false
            on { getConfig("connectionConfig") } doReturn connectionConfiguration
        }

        val gatewayConfig = config.toGatewayConfiguration()

        assertThat(gatewayConfig).isEqualTo(
            GatewayConfiguration(
                hostPort = 231,
                hostAddress = "address",
                connectionConfig = ConnectionConfiguration(
                    maxClientConnections = 100,
                    acquireTimeout = 5.minutes,
                    connectionIdleTimeout = 10.hours,
                    responseTimeout = 20.seconds,
                    retryDelay = 21.minutes
                ),
                sslConfig = SslConfiguration(
                    rawTrustStore = byteArrayOf(4, 5),
                    rawKeyStore = byteArrayOf(1, 2, 3),
                    keyStorePassword = "pass1",
                    trustStorePassword = "pass2",
                    revocationCheck =
                    RevocationConfig(RevocationConfigMode.HARD_FAIL)
                )
            )
        )
    }
}
