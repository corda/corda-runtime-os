package net.corda.membership.lib.impl.converter

import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.membership.lib.EndpointInfoFactory
import net.corda.membership.lib.impl.MemberContextImpl
import net.corda.utilities.parse
import net.corda.utilities.parseList
import net.corda.v5.base.exceptions.ValueNotFoundException
import net.corda.v5.membership.EndpointInfo
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EndpointInfoConverterTest {
    companion object {
        private val endpointInfoFactory: EndpointInfoFactory = mock {
            on { create(any(), any()) } doAnswer { invocation ->
                mock {
                    on { this.url } doReturn invocation.getArgument(0)
                    on { this.protocolVersion } doReturn invocation.getArgument(1)
                }
            }
        }
        private val endpoint = endpointInfoFactory.create("https://localhost:10000")
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
        with(memberContext.parse<EndpointInfo>("corda.endpoint")) {
            assertEquals(endpoint.url, this.url)
            assertEquals(endpoint.protocolVersion, this.protocolVersion)
        }
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
        assertEquals(endpoint.url, list[0].url)
        assertEquals(endpoint.protocolVersion, list[0].protocolVersion)
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