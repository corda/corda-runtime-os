package net.corda.membership.lib.impl

import net.corda.v5.base.util.NetworkHostAndPort
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EndpointInfoImplTest {
    companion object {
        private const val URL = "https://localhost:10000"
        private const val HOST = "localhost"
        private const val PORT = 10000
        private const val DEFAULT_PROTOCOL_VERSION = 1
    }

    @Test
    fun `creating EndpointInfo using default protocol version`() {
        val endpointInfo = EndpointInfoImpl(URL)
        assertEquals(URL, endpointInfo.url)
        assertEquals(DEFAULT_PROTOCOL_VERSION, endpointInfo.protocolVersion)
    }

    @Test
    fun `creating EndpointInfo`() {
        val protocolVersion = 99
        val endpointInfo = EndpointInfoImpl(URL, protocolVersion)
        assertEquals(URL, endpointInfo.url)
        assertEquals(protocolVersion, endpointInfo.protocolVersion)
    }

    @Test
    fun `NetworkHostAndPort to EndpointInfo works with default protocol version`() {
        val networkHostAndPort = NetworkHostAndPort(HOST, PORT)
        val endpointInfo = networkHostAndPort.toEndpointInfo()
        assertEquals(URL, endpointInfo.url)
        assertEquals(DEFAULT_PROTOCOL_VERSION, endpointInfo.protocolVersion)
    }

    @Test
    fun `NetworkHostAndPort to EndpointInfo works`() {
        val protocolVersion = 99
        val networkHostAndPort = NetworkHostAndPort(HOST, PORT)
        val endpointInfo = networkHostAndPort.toEndpointInfo(protocolVersion)
        assertEquals(URL, endpointInfo.url)
        assertEquals(protocolVersion, endpointInfo.protocolVersion)
    }
}