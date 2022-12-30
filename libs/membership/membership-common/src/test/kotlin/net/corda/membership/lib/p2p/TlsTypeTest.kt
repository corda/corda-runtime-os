package net.corda.membership.lib.p2p

import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.ConfigKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class TlsTypeTest {
    @Test
    fun `getClusterType throws exception if the gateway configuration was not set`() {
        assertThrows<TlsType.FailToReadClusterTlsTypeException> {
            TlsType.getClusterType {
                null
            }
        }
    }

    @Test
    fun `getClusterType returns the correct value if available`() {
        val sslConfig = mock<SmartConfig> {
            on { getString("tlsType") } doReturn "MUTUAL"
        }
        val gatewayConfig = mock<SmartConfig> {
            on { getConfig("sslConfig") } doReturn sslConfig
        }

        val tlsType = TlsType.getClusterType {
            if (it == ConfigKeys.P2P_GATEWAY_CONFIG) {
                gatewayConfig
            } else {
                null
            }
        }

        assertThat(tlsType).isEqualTo(TlsType.MUTUAL)
    }
}