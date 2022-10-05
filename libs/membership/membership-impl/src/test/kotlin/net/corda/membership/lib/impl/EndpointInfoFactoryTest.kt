package net.corda.membership.lib.impl

import net.corda.utilities.NetworkHostAndPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EndpointInfoFactoryTest {
    companion object {
        private const val URL = "https://localhost:10000"
        private const val HOST = "localhost"
        private const val PORT = 10000
        private const val DEFAULT_PROTOCOL_VERSION = 1

        private val endpointInfoFactory = EndpointInfoFactoryImpl()
    }

    @Test
    fun `factory builds EndpointInfo using default protocol version`() {
        val endpointInfo = endpointInfoFactory.create(URL)
        assertThat(endpointInfo.url).isEqualTo(URL)
        assertThat(endpointInfo.protocolVersion).isEqualTo(DEFAULT_PROTOCOL_VERSION)
    }

    @Test
    fun `factory builds EndpointInfo`() {
        val protocolVersion = 99
        val endpointInfo = endpointInfoFactory.create(URL, protocolVersion)
        assertThat(endpointInfo.url).isEqualTo(URL)
        assertThat(endpointInfo.protocolVersion).isEqualTo(protocolVersion)
    }

    @Test
    fun `NetworkHostAndPort to EndpointInfo works with default protocol version`() {
        val networkHostAndPort = NetworkHostAndPort(HOST, PORT)
        val endpointInfo = networkHostAndPort.toEndpointInfo()
        assertThat(endpointInfo.url).isEqualTo(URL)
        assertThat(endpointInfo.protocolVersion).isEqualTo(DEFAULT_PROTOCOL_VERSION)
    }

    @Test
    fun `NetworkHostAndPort to EndpointInfo works`() {
        val protocolVersion = 99
        val networkHostAndPort = NetworkHostAndPort(HOST, PORT)
        val endpointInfo = networkHostAndPort.toEndpointInfo(protocolVersion)
        assertThat(endpointInfo.url).isEqualTo(URL)
        assertThat(endpointInfo.protocolVersion).isEqualTo(protocolVersion)
    }
}
