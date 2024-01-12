package net.corda.membership.lib.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EndpointInfoImplTest {
    companion object {
        private const val URL = "https://localhost:10000"
        private const val DEFAULT_PROTOCOL_VERSION = 1
    }

    @Test
    fun `creating EndpointInfo using default protocol version`() {
        val endpointInfo = EndpointInfoImpl(URL)
        assertThat(endpointInfo.url).isEqualTo(URL)
        assertThat(endpointInfo.protocolVersion).isEqualTo(DEFAULT_PROTOCOL_VERSION)
    }

    @Test
    fun `creating EndpointInfo`() {
        val protocolVersion = 99
        val endpointInfo = EndpointInfoImpl(URL, protocolVersion)
        assertThat(endpointInfo.url).isEqualTo(URL)
        assertThat(endpointInfo.protocolVersion).isEqualTo(protocolVersion)
    }
}
