package net.corda.membership.lib.impl.converter

import net.corda.layeredpropertymap.CustomPropertyConverter
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.membership.lib.impl.MemberContextImpl
import net.corda.v5.base.exceptions.ValueNotFoundException
import net.corda.v5.base.util.parse
import net.corda.v5.base.util.parseList
import net.corda.v5.base.util.parseOrNull
import net.corda.v5.cipher.suite.KeyEncodingService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        val memberContext = LayeredPropertyMapMocks.create<MemberContextImpl>(
            sortedMapOf("corda.ledgerKey" to LEDGER_KEY),
            converters
        )
        val result = memberContext.parse<PublicKey>("corda.ledgerKey")
        assertEquals(ledgerKey, result)
    }

    @Test
    fun `converting list of public key should work`() {
        val memberContext = LayeredPropertyMapMocks.create<MemberContextImpl>(
            sortedMapOf("corda.ledgerKeys.0" to LEDGER_KEY),
            converters
        )
        val result = memberContext.parseList<PublicKey>("corda.ledgerKeys")
        assertEquals(1, result.size)
        assertEquals(ledgerKey, result[0])
    }

    @Test
    fun `converting PublicKey fails when the keys is null`() {
        val memberContext = LayeredPropertyMapMocks.create<MemberContextImpl>(
            sortedMapOf(
                "corda.ledgerKey" to null
            ),
            converters
        )
        assertFailsWith<ValueNotFoundException> { memberContext.parse<PublicKey>("corda.ledgerKey") }
    }

    @Test
    fun `converting list of PublicKeys fails when the keys is null`() {
        val memberContext = LayeredPropertyMapMocks.create<MemberContextImpl>(
            sortedMapOf("corda.ledgerKeys.0" to null),
            converters
        )
        assertFailsWith<ValueNotFoundException> {
            memberContext.parseList<PublicKey>("corda.ledgerKeys")
        }
    }

    @Test
    fun `converting to nullable PublicKey works`() {
        val memberContext = LayeredPropertyMapMocks.create<MemberContextImpl>(
            sortedMapOf("corda.ledgerKey" to null),
            converters
        )
        val result = memberContext.parseOrNull<PublicKey>("corda.ledgerKey")
        assertNull(result)
    }
}