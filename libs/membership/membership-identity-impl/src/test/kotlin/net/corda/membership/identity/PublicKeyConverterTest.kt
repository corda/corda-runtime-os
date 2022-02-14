package net.corda.membership.identity

import net.corda.membership.conversion.PropertyConverterImpl
import net.corda.membership.identity.MemberInfoExtension.Companion.IDENTITY_KEYS
import net.corda.membership.identity.MemberInfoExtension.Companion.PARTY_OWNING_KEY
import net.corda.membership.identity.converter.PublicKeyConverter
import net.corda.membership.testkit.createContext
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import java.lang.IllegalArgumentException
import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class PublicKeyConverterTest {
    companion object {
        private val keyEncodingService = Mockito.mock(CipherSchemeMetadata::class.java)

        private const val IDENTITY_KEY_KEY = "corda.identityKeys.1"
        private const val IDENTITY_KEY = "12345"
        private val identityKey = Mockito.mock(PublicKey::class.java)

        private const val OWNING_KEY = "12378"
        private val owningKey = Mockito.mock(PublicKey::class.java)

        private val converter = PropertyConverterImpl(listOf(PublicKeyConverter(keyEncodingService)))
        private val publicKeyConverter = converter.customConverters.first()
    }

    @BeforeEach
    fun setUp() {
        whenever(
            keyEncodingService.decodePublicKey(IDENTITY_KEY)
        ).thenReturn(identityKey)
        whenever(
            keyEncodingService.encodeAsString(identityKey)
        ).thenReturn(IDENTITY_KEY)

        whenever(
            keyEncodingService.decodePublicKey(OWNING_KEY)
        ).thenReturn(owningKey)
        whenever(
            keyEncodingService.encodeAsString(owningKey)
        ).thenReturn(OWNING_KEY)
    }

    @Test
    fun `converting identity key should work`() {
        val memberContext = createContext(
            sortedMapOf(IDENTITY_KEY_KEY to IDENTITY_KEY),
            converter,
            MemberContextImpl::class.java,
            IDENTITY_KEYS
        )

        val result = publicKeyConverter.convert(memberContext)
        assertEquals(identityKey, result)
        assertNotEquals(owningKey, result)
    }

    @Test
    fun `converting owning key should work`() {
        val memberContext = createContext(
            sortedMapOf(
                PARTY_OWNING_KEY to OWNING_KEY,
                IDENTITY_KEY_KEY to IDENTITY_KEY,
                "key" to "value"
            ),
            converter,
            MemberContextImpl::class.java,
            PARTY_OWNING_KEY
        )

        val result = publicKeyConverter.convert(memberContext)
        assertEquals(owningKey, result)
        assertNotEquals(identityKey, result)
    }

    @Test
    fun `converting PublicKey fails when invalid context is used`() {
        val mgmContext = createContext(
            sortedMapOf(
                PARTY_OWNING_KEY to OWNING_KEY,
                IDENTITY_KEY_KEY to IDENTITY_KEY,
                "key" to "value"
            ),
            converter,
            MGMContextImpl::class.java,
            PARTY_OWNING_KEY
        )

        val ex = assertFailsWith<IllegalArgumentException> { publicKeyConverter.convert(mgmContext) }
        assertEquals("Unknown class '${mgmContext.store::class.java.name}'.", ex.message)
    }
}