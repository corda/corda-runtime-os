package net.corda.membership.identity

import net.corda.membership.conversion.PropertyConverterImpl
import net.corda.membership.identity.MemberInfoExtension.Companion.IDENTITY_KEY_HASHES
import net.corda.membership.identity.converter.PublicKeyHashConverter
import net.corda.membership.testkit.createContext
import net.corda.v5.crypto.PublicKeyHash
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class PublicKeyHashConverterTest {
    companion object {
        private const val IDENTITY_KEY_HASH_KEY = "corda.identityKeyHashes.1"
        private const val IDENTITY_KEY_HASH = "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD"
        private val identityKeyHash = PublicKeyHash.parse(IDENTITY_KEY_HASH)

        private const val OWNING_KEY_HASH = "CB8379AC2098AA165029E3938A51DA0BCECFC008FD6795F401178647F96C5B34"
        private val owningKeyHash = PublicKeyHash.parse(OWNING_KEY_HASH)

        private val converter = PropertyConverterImpl(listOf(PublicKeyHashConverter()))
        private val publicKeyHashConverter = converter.customConverters.first()
    }

    @Test
    fun `converting identity key should work`() {
        val memberContext = createContext(
            sortedMapOf(IDENTITY_KEY_HASH_KEY to IDENTITY_KEY_HASH),
            converter,
            MemberContextImpl::class.java,
            IDENTITY_KEY_HASHES
        )

        val result = publicKeyHashConverter.convert(memberContext)
        assertEquals(identityKeyHash, result)
        assertNotEquals(owningKeyHash, result)
    }

    @Test
    fun `converting PublicKeyHash fails when invalid context is used`() {
        val mgmContext = createContext(
            sortedMapOf(
                IDENTITY_KEY_HASH_KEY to IDENTITY_KEY_HASH,
                "key" to "value"
            ),
            converter,
            MGMContextImpl::class.java,
            IDENTITY_KEY_HASHES
        )

        val ex = assertFailsWith<IllegalArgumentException> { publicKeyHashConverter.convert(mgmContext) }
        assertEquals("Unknown class '${mgmContext.store::class.java.name}'.", ex.message)
    }
}
