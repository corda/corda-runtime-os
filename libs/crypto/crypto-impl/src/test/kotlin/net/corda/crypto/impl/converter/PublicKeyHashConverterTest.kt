package net.corda.crypto.impl.converter

import net.corda.crypto.cipher.suite.PublicKeyHash
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.v5.base.types.LayeredPropertyMap
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PublicKeyHashConverterTest {
    companion object {
        private const val LEDGER_KEY_HASH = "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD"
        private val ledgerKeyHash = PublicKeyHash.parse(LEDGER_KEY_HASH)
        private val converters = listOf(PublicKeyHashConverter())
    }

    @Test
    fun `converting hash should work for single element`() {
        val conversionContext = LayeredPropertyMapMocks.createConversionContext<LayeredContextImpl>(
            sortedMapOf(
                "corda.ledger.keys" to LEDGER_KEY_HASH
            ),
            converters,
            "corda.ledger.keys"
        )
        val result = converters[0].convert(conversionContext)
        assertEquals(ledgerKeyHash, result)
    }

    @Test
    fun `converting hash fails when the keys is null`() {
        val conversionContext = LayeredPropertyMapMocks.createConversionContext<LayeredContextImpl>(
            sortedMapOf(
                "corda.ledger.keys" to LEDGER_KEY_HASH
            ),
            converters,
            ""
        )
        val result = converters[0].convert(conversionContext)
        assertNull(result)
    }

    @Test
    fun `converting to nullable hash works`() {
        val conversionContext = LayeredPropertyMapMocks.createConversionContext<LayeredContextImpl>(
            sortedMapOf(
                "corda.ledger.keys" to null
            ),
            converters,
            "corda.ledger.keys"
        )
        val result = converters[0].convert(conversionContext)
        assertNull(result)
    }

    interface LayeredContext : LayeredPropertyMap

    class LayeredContextImpl(
        private val map: LayeredPropertyMap
    ) : LayeredPropertyMap by map, LayeredContext
}
