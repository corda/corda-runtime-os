package net.corda.crypto.service.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.GeneratedWrappedKey
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.crypto.service.CryptoServiceRef
import net.corda.utilities.toByteArray
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CryptoServiceRefUtilsTests {
    companion object {
        private lateinit var schemeMetadata: CipherSchemeMetadata

        @JvmStatic
        @BeforeAll
        fun setup() {
            schemeMetadata = CipherSchemeMetadataImpl()
        }
    }

    @Test
    fun `Should convert to SigningWrappedKeySaveContext`() {
        val scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME)
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.Categories.LEDGER,
            masterKeyAlias = UUID.randomUUID().toString()
        )
        val generatedKey = GeneratedWrappedKey(
            publicKey = mock(),
            keyMaterial = UUID.randomUUID().toByteArray(),
            encodingVersion = 12
        )
        val alias = UUID.randomUUID().toString()
        val externalId = UUID.randomUUID().toString()
        val result = ref.toSaveKeyContext(generatedKey, alias, scheme, externalId)
        assertInstanceOf(SigningWrappedKeySaveContext::class.java, result)
        assertSame(generatedKey, result.key)
        assertEquals(alias, result.alias)
        assertEquals(scheme, result.keyScheme)
        assertEquals(ref.category, result.category)
        assertEquals(ref.masterKeyAlias, result.wrappingKeyAlias)
        assertEquals(externalId, result.externalId)
    }
}