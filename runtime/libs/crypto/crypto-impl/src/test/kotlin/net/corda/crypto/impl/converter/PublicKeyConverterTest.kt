package net.corda.crypto.impl.converter

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.layeredpropertymap.CustomPropertyConverter
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.v5.base.types.LayeredPropertyMap
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PublicKeyConverterTest {
    companion object {
        private const val LEDGER_KEY = "12345"
        private val ledgerKey = mock<PublicKey>()
    }

    private lateinit var keyEncodingService: KeyEncodingService
    private lateinit var converters: List<CustomPropertyConverter<out Any>>

    @BeforeEach
    fun setup() {
        keyEncodingService = mock {
            on { decodePublicKey(LEDGER_KEY) } doReturn ledgerKey
            on { encodeAsString(ledgerKey) } doReturn LEDGER_KEY
        }
        converters = listOf(PublicKeyConverter(keyEncodingService))
    }

    @Test
    fun `converting public key should work for single element`() {
        val conversionContext = LayeredPropertyMapMocks.createConversionContext<LayeredContextImpl>(
            sortedMapOf(
                "corda.ledger.keys" to LEDGER_KEY
            ),
            converters,
            "corda.ledger.keys"
        )

        val result = converters[0].convert(conversionContext)
        assertEquals(ledgerKey, result)
    }

    @Test
    fun `converting PublicKey fails when the keys is null`() {
        val conversionContext = LayeredPropertyMapMocks.createConversionContext<LayeredContextImpl>(
            sortedMapOf(
                "corda.ledger.keys" to LEDGER_KEY
            ),
            converters,
            ""
        )
        val result = converters[0].convert(conversionContext)
        assertNull(result)
    }

    @Test
    fun `converting to nullable PublicKey works`() {
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

    private interface LayeredContext : LayeredPropertyMap

    private class LayeredContextImpl(
        private val map: LayeredPropertyMap
    ) : LayeredPropertyMap by map, LayeredContext
}