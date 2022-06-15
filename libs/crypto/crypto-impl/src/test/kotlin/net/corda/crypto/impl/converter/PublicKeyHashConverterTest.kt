package net.corda.crypto.impl.converter

import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.membership.impl.MemberContextImpl
import net.corda.v5.base.exceptions.ValueNotFoundException
import net.corda.v5.base.util.parse
import net.corda.v5.base.util.parseOrNull
import net.corda.v5.base.util.parseSet
import net.corda.v5.crypto.PublicKeyHash
import org.junit.jupiter.api.Test
import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PublicKeyHashConverterTest {
    companion object {
        private const val LEDGER_KEY_HASH = "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD"
        private val ledgerKeyHash = PublicKeyHash.parse(LEDGER_KEY_HASH)
        private val converters = listOf(PublicKeyHashConverter())
    }

    @Test
    fun `converting hash should work for single element`() {
        val memberContext = LayeredPropertyMapMocks.create<MemberContextImpl>(
            sortedMapOf("corda.ledgerKeyHash" to LEDGER_KEY_HASH),
            converters
        )
        val result = memberContext.parse<PublicKeyHash>("corda.ledgerKeyHash")
        assertEquals(ledgerKeyHash, result)
    }

    @Test
    fun `converting list of hashes should work`() {
        val memberContext = LayeredPropertyMapMocks.create<MemberContextImpl>(
            sortedMapOf("corda.ledgerKeyHashes.0" to LEDGER_KEY_HASH),
            converters
        )
        val result = memberContext.parseSet<PublicKeyHash>("corda.ledgerKeyHashes")
        assertEquals(1, result.size)
        assertTrue(result.contains(ledgerKeyHash))
    }

    @Test
    fun `converting hash fails when the keys is null`() {
        val memberContext = LayeredPropertyMapMocks.create<MemberContextImpl>(
            sortedMapOf("corda.ledgerKeyHash" to null),
            converters
        )
        assertFailsWith<ValueNotFoundException> { memberContext.parse<PublicKey>("corda.ledgerKeyHash") }
    }

    @Test
    fun `converting list of hashes fails when the keys is null`() {
        val memberContext = LayeredPropertyMapMocks.create<MemberContextImpl>(
            sortedMapOf("corda.ledgerKeyHashes.0" to null),
            converters
        )
        assertFailsWith<ValueNotFoundException> {
            memberContext.parseSet<PublicKey>("corda.ledgerKeyHashes")
        }
    }

    @Test
    fun `converting to nullable hash works`() {
        val memberContext = LayeredPropertyMapMocks.create<MemberContextImpl>(
            sortedMapOf("corda.ledgerKeyHash" to null),
            converters
        )
        val result = memberContext.parseOrNull<PublicKey>("corda.ledgerKeyHash")
        assertNull(result)
    }
}
