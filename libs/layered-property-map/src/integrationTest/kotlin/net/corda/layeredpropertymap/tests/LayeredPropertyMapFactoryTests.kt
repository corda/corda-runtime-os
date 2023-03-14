package net.corda.layeredpropertymap.tests

import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.create
import net.corda.layeredpropertymap.tests.converters.IntegrationDummyEndpointInfo
import net.corda.utilities.parse
import net.corda.utilities.parseList
import net.corda.v5.base.types.LayeredPropertyMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
class LayeredPropertyMapFactoryTests {

    @InjectService(timeout = 5000L)
    lateinit var facttory: LayeredPropertyMapFactory

    private fun createMap() = mapOf<String, String?>(
            "number" to "42",
            "corda.endpoints.2.url" to "localhost3",
            "corda.endpoints.2.protocolVersion" to "3",
            "corda.endpoints.0.url" to "localhost1",
            "corda.endpoints.0.protocolVersion" to "1",
            "corda.endpoints.1.url" to "localhost2",
            "corda.endpoints.1.protocolVersion" to "2",
            "listWithNull.0" to "45"
        )

    @Test
    fun `Should be able to create instance of LayeredPropertyMap with custom converters`() {
        val layeredPropertyMap = facttory.createMap(createMap())
        assertEquals(42, layeredPropertyMap.parse("number"))
        val complexList = layeredPropertyMap.parseList<IntegrationDummyEndpointInfo>("corda.endpoints")
        assertEquals(3, complexList.size)
        for( i in 0 until  3) {
            assertEquals("localhost${i+1}", complexList[i].url)
            assertEquals(i+1, complexList[i].protocolVersion)
        }
        val simpleList = layeredPropertyMap.parseList<Int>("listWithNull")
        assertEquals(1, simpleList.size)
        assertEquals(45, simpleList[0])
    }

    @Test
    fun `Should be able to create instance of derived class with custom converters`() {
        val layeredContext = facttory.create<LayeredContextImpl>(createMap())
        assertEquals(42, layeredContext.parse("number"))
        val complexList = layeredContext.parseList<IntegrationDummyEndpointInfo>("corda.endpoints")
        assertEquals(3, complexList.size)
        for( i in 0 until  3) {
            assertEquals("localhost${i+1}", complexList[i].url)
            assertEquals(i+1, complexList[i].protocolVersion)
        }
        val simpleList = layeredContext.parseList<Int>("listWithNull")
        assertEquals(1, simpleList.size)
        assertEquals(45, simpleList[0])
    }

    interface LayeredContext : LayeredPropertyMap

    class LayeredContextImpl(
        private val map: LayeredPropertyMap
    ) : LayeredPropertyMap by map, LayeredContext
}