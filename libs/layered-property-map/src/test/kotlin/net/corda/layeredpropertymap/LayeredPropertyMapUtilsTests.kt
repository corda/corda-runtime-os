package net.corda.layeredpropertymap

import net.corda.layeredpropertymap.impl.LayeredPropertyMapImpl
import net.corda.layeredpropertymap.impl.PropertyConverter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LayeredPropertyMapUtilsTests {
    private fun createLayeredPropertyMapImpl() = LayeredPropertyMapImpl(
        mapOf(
            "corda.endpoints.2.url" to "localhost3",
            "corda.endpoints.2.protocolVersion" to "3",
            "corda.endpoints.0.url" to "localhost1",
            "corda.endpoints.0.protocolVersion" to "1",
            "corda.endpoints.1.url" to "localhost2",
            "corda.endpoints.1.protocolVersion" to "2",
            "listWithNull.0" to "42"
        ),
        PropertyConverter(
            mapOf(
                DummyObjectWithNumberAndText::class.java to DummyConverter(),
                DummyEndpointInfo::class.java to DummyEndpointInfoConverter()
            )
        )
    )

    @Test
    fun `Should convert to avro format`() {
        val original = createLayeredPropertyMapImpl()
        val avro = original.toAvro()
        assertNotNull(avro)
        assertTrue(avro.items.isNotEmpty())
        assertEquals(original.entries.size, avro.items.size)
        original.entries.forEach { entry ->
            val item = avro.items.firstOrNull { it.key == entry.key }
            assertNotNull(item)
            assertEquals(entry.value, item.value)
        }
    }
}