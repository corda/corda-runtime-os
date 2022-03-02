package net.corda.membership.impl

import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.membership.impl.converter.EndpointInfoConverter
import net.corda.v5.base.exceptions.ValueNotFoundException
import net.corda.v5.base.util.parse
import net.corda.v5.base.util.parseList
import net.corda.v5.membership.EndpointInfo
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EndpointInfoConverterTest {
    companion object {
        private val endpoint = EndpointInfoImpl("https://localhost:10000", EndpointInfo.DEFAULT_PROTOCOL_VERSION)
        private val converters = listOf(EndpointInfoConverter())
    }

    @Test
    fun `converting EndpointInfo should work for single element`() {
        val memberContext = LayeredPropertyMapMocks.create<MemberContextImpl>(
            sortedMapOf(
                "corda.endpoint.connectionURL" to endpoint.url,
                "corda.endpoint.protocolVersion" to endpoint.protocolVersion.toString()
            ),
            converters
        )
        assertEquals(endpoint, memberContext.parse<EndpointInfo>("corda.endpoint"))
    }

    @Test
    fun `converting EndpointInfo should work for list`() {
        val memberContext = LayeredPropertyMapMocks.create<MemberContextImpl>(
            sortedMapOf(
                "corda.endpoints.0.connectionURL" to endpoint.url,
                "corda.endpoints.0.protocolVersion" to endpoint.protocolVersion.toString()
            ),
            converters
        )
        val list = memberContext.parseList<EndpointInfo>("corda.endpoints")
        assertEquals(1, list.size)
        assertEquals(endpoint, list[0])
    }

    @Test
    fun `converting EndpointInfo fails when one of the keys is null`() {
        val memberContext = LayeredPropertyMapMocks.create<MemberContextImpl>(
            sortedMapOf(
                "corda.endpoint.connectionURL" to endpoint.url,
                "corda.endpoint.protocolVersion" to null
            ),
            converters
        )
        assertFailsWith<ValueNotFoundException> { memberContext.parse<EndpointInfo>("corda.endpoint") }
    }
}