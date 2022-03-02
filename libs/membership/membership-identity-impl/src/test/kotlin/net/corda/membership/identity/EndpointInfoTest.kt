package net.corda.membership.identity

import net.corda.v5.base.util.NetworkHostAndPort
import net.corda.v5.membership.EndpointInfo
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EndpointInfoTest {
    companion object {
        private const val URL = "https://localhost:10000"
        private const val HOST = "localhost"
        private const val PORT = 10000
    }

    @Test
    fun `creating EndpointInfo`() {
        val endpointInfo = EndpointInfoImpl(URL, EndpointInfo.DEFAULT_PROTOCOL_VERSION)
        assertEquals(URL, endpointInfo.url)
        assertEquals(EndpointInfo.DEFAULT_PROTOCOL_VERSION, endpointInfo.protocolVersion)
    }

    @Test
    fun `NetworkHostAndPort to EndpointInfo works`() {
        val networkHostAndPort = NetworkHostAndPort(HOST, PORT)
        val endpointInfo = networkHostAndPort.toEndpointInfo(EndpointInfo.DEFAULT_PROTOCOL_VERSION)
        assertEquals(URL, endpointInfo.url)
        assertEquals(EndpointInfo.DEFAULT_PROTOCOL_VERSION, endpointInfo.protocolVersion)
    }
}