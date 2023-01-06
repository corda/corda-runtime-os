package net.corda.p2p.gateway.messaging

import com.typesafe.config.Config
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class SslConfigurationTest {
    @Test
    fun `toSslConfiguration return correct configuration`() {
        val config = mock<Config> {
            on { getEnum(RevocationConfigMode::class.java, "revocationCheck.mode") } doReturn RevocationConfigMode.SOFT_FAIL
            on { getEnum(TlsType::class.java, "tlsType") } doReturn TlsType.MUTUAL
        }

        val sslConfiguration = config.toSslConfiguration()

        assertThat(sslConfiguration).isEqualTo(
            SslConfiguration(
                revocationCheck = RevocationConfig(RevocationConfigMode.SOFT_FAIL),
                tlsType = TlsType.MUTUAL
            )
        )
    }
}
