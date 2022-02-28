package net.corda.membership.identity

import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.membership.identity.converter.PublicKeyHashConverter
import net.corda.v5.base.exceptions.ValueNotFoundException
import net.corda.v5.base.util.parse
import net.corda.v5.base.util.parseList
import net.corda.v5.base.util.parseOrNull
import net.corda.v5.crypto.PublicKeyHash
import org.junit.jupiter.api.Test
import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PublicKeyHashConverterTest {
    companion object {
        private const val IDENTITY_KEY_HASH = "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD"
        private val identityKeyHash = PublicKeyHash.parse(IDENTITY_KEY_HASH)
        private val converters = listOf(PublicKeyHashConverter())
    }

    @Test
    fun `converting hash should work for single element`() {
        val memberContext = LayeredPropertyMapMocks.create<MemberContextImpl>(
            sortedMapOf("corda.identityKeyHash" to IDENTITY_KEY_HASH),
            converters
        )
        val result = memberContext.parse<PublicKeyHash>("corda.identityKeyHash")
        assertEquals(identityKeyHash, result)
    }

    @Test
    fun `converting list of hashes should work`() {
        val memberContext = LayeredPropertyMapMocks.create<MemberContextImpl>(
            sortedMapOf("corda.identityKeyHashes.0" to IDENTITY_KEY_HASH),
            converters
        )
        val result = memberContext.parseList<PublicKeyHash>("corda.identityKeyHashes")
        assertEquals(1, result.size)
        assertEquals(identityKeyHash, result[0])
    }

    @Test
    fun `converting hash fails when the keys is null`() {
        val memberContext = LayeredPropertyMapMocks.create<MemberContextImpl>(
            sortedMapOf("corda.identityKeyHash" to null),
            converters
        )
        assertFailsWith<ValueNotFoundException> { memberContext.parse<PublicKey>("corda.identityKeyHash") }
    }

    @Test
    fun `converting list of hashes fails when the keys is null`() {
        val memberContext = LayeredPropertyMapMocks.create<MemberContextImpl>(
            sortedMapOf("corda.identityKeyHashes.0" to null),
            converters
        )
        assertFailsWith<ValueNotFoundException> {
            memberContext.parseList<PublicKey>("corda.identityKeyHashes")
        }
    }

    @Test
    fun `converting to nullable hash works`() {
        val memberContext = LayeredPropertyMapMocks.create<MemberContextImpl>(
            sortedMapOf("corda.identityKeyHash" to null),
            converters
        )
        val result = memberContext.parseOrNull<PublicKey>("corda.identityKeyHash")
        assertNull(result)
    }
}
