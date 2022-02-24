package net.corda.membership.identity

import net.corda.layeredpropertymap.impl.PropertyConverter
import net.corda.membership.identity.MemberInfoExtension.Companion.ENDPOINTS
import net.corda.membership.identity.converter.EndpointInfoConverter
import net.corda.membership.testkit.createContext
import net.corda.v5.membership.identity.EndpointInfo
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EndpointInfoConverterTest {
    companion object {
        private const val URL_KEY = "corda.endpoints.1.connectionURL"
        private const val PROTOCOL_VERSION = "corda.endpoints.1.protocolVersion"
        private val endpoint = EndpointInfoImpl("https://localhost:10000", EndpointInfo.DEFAULT_PROTOCOL_VERSION)
        private val converter = PropertyConverter(listOf(EndpointInfoConverter()))
        private val endpointInfoConverter = converter.customConverters.first()
    }

    @Test
    fun `converting EndpointInfo should work`() {
        val memberContext = createContext(
            sortedMapOf(
                URL_KEY to endpoint.url,
                PROTOCOL_VERSION to endpoint.protocolVersion.toString()
            ),
            converter,
            MemberContextImpl::class.java,
            ENDPOINTS
        )

        assertEquals(endpoint, endpointInfoConverter.convert(memberContext))
    }

    @Test
    fun `converting EndpointInfo fails when invalid context is used`() {
        val mgmContext = createContext(
            sortedMapOf(
                URL_KEY to endpoint.url,
                PROTOCOL_VERSION to endpoint.protocolVersion.toString()
            ),
            converter,
            MGMContextImpl::class.java,
            ENDPOINTS
        )

        val ex = assertFailsWith<IllegalArgumentException> { endpointInfoConverter.convert(mgmContext) }
        assertEquals("Unknown class '${mgmContext.storeClass.name}'.", ex.message)
    }

    @Test
    fun `converting EndpointInfo fails when one of the keys is null`() {
        val memberContext = createContext(
            sortedMapOf(
                URL_KEY to endpoint.url,
                PROTOCOL_VERSION to null
            ),
            converter,
            MemberContextImpl::class.java,
            ENDPOINTS
        )

        val ex = assertFailsWith<IllegalArgumentException> { endpointInfoConverter.convert(memberContext) }
        assertEquals("protocolVersion cannot be null.", ex.message)
    }
}