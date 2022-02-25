package net.corda.membership.identity

import net.corda.layeredpropertymap.CustomPropertyConverter
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.membership.identity.converter.PublicKeyConverter
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
        private const val IDENTITY_KEY = "12345"
        private val identityKey = mock<PublicKey>()
    }

    private lateinit var keyEncodingService: KeyEncodingService
    private lateinit var converters: List<CustomPropertyConverter<out Any>>

    @BeforeEach
    fun setup() {
        keyEncodingService = mock {
            on { decodePublicKey(IDENTITY_KEY) } doReturn identityKey
            on { encodeAsString(identityKey) } doReturn IDENTITY_KEY
        }
        converters = listOf(PublicKeyConverter(keyEncodingService))
    }

    @Test
    fun `converting public key should work for single element`() {
        val memberContext = LayeredPropertyMapMocks.create<MemberContextImpl>(
            sortedMapOf("corda.identityKey" to IDENTITY_KEY),
            converters
        )
        val result = memberContext.parse<PublicKey>("corda.identityKey")
        assertEquals(identityKey, result)
    }

    @Test
    fun `converting list of public key should work`() {
        val memberContext = LayeredPropertyMapMocks.create<MemberContextImpl>(
            sortedMapOf("corda.identityKeys.0" to IDENTITY_KEY),
            converters
        )
        val result = memberContext.parseList<PublicKey>("corda.identityKeys")
        assertEquals(1, result.size)
        assertEquals(identityKey, result[0])
    }

    @Test
    fun `converting PublicKey fails when the keys is null`() {
        val memberContext = LayeredPropertyMapMocks.create<MemberContextImpl>(
            sortedMapOf(
                "corda.identityKey" to null
            ),
            converters
        )
        assertFailsWith<ValueNotFoundException> { memberContext.parse<PublicKey>("corda.identityKey") }
    }

    @Test
    fun `converting list of PublicKeys fails when the keys is null`() {
        val memberContext = LayeredPropertyMapMocks.create<MemberContextImpl>(
            sortedMapOf("corda.identityKeys.0" to null),
            converters
        )
        assertFailsWith<ValueNotFoundException> {
            memberContext.parseList<PublicKey>("corda.identityKeys")
        }
    }

    @Test
    fun `converting to nullable PublicKey works`() {
        val memberContext = LayeredPropertyMapMocks.create<MemberContextImpl>(
            sortedMapOf("corda.identityKey" to null),
            converters
        )
        val result = memberContext.parseOrNull<PublicKey>("corda.identityKey")
        assertNull(result)
    }
}