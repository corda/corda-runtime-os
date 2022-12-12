package net.corda.crypto.softhsm.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.KeyMaterialSpec
import net.corda.crypto.component.test.utils.generateKeyPair
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.softhsm.PRIVATE_KEY_ENCODING_VERSION
import net.corda.crypto.softhsm.SoftWrappingKeyMap
import net.corda.v5.crypto.EDDSA_ED25519_CODE_NAME
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.test.assertEquals

class DefaultSoftPrivateKeyWrappingTests {
    companion object {
        private lateinit var schemeMetadata: CipherSchemeMetadata

        @JvmStatic
        @BeforeAll
        fun setup() {
            schemeMetadata = CipherSchemeMetadataImpl()
        }
    }

    @Test
    fun `Should unwrap valid key material`() {
        val master = WrappingKey.generateWrappingKey(schemeMetadata)
        val keyPair = generateKeyPair(schemeMetadata, EDDSA_ED25519_CODE_NAME)
        val alias = "master-alias"
        val spec = KeyMaterialSpec(
            master.wrap(keyPair.private),
            alias,
            PRIVATE_KEY_ENCODING_VERSION,
        )
        val wrappingKeyMap = mock<SoftWrappingKeyMap> {
            on { getWrappingKey(alias) } doReturn master
        }
        val cut = DefaultSoftPrivateKeyWrapping(wrappingKeyMap)
        val key = cut.unwrap(spec)
        assertEquals(keyPair.private, key)
    }

    @Test
    fun `Should fail unwrap if master key alias in spec is null or empty`() {
        val master = WrappingKey.generateWrappingKey(schemeMetadata)
        val keyPair = generateKeyPair(schemeMetadata, EDDSA_ED25519_CODE_NAME)
        val wrappingKeyMap = mock<SoftWrappingKeyMap>()
        val cut = DefaultSoftPrivateKeyWrapping(wrappingKeyMap)
        assertThrows<IllegalArgumentException> {
            cut.unwrap(
                KeyMaterialSpec(
                    master.wrap(keyPair.private),
                    null,
                    PRIVATE_KEY_ENCODING_VERSION,
                )
            )
        }
        assertThrows<IllegalArgumentException> {
            cut.unwrap(
                KeyMaterialSpec(
                    master.wrap(keyPair.private),
                    "",
                    PRIVATE_KEY_ENCODING_VERSION,
                )
            )
        }
    }

    @Test
    fun `Should wrap private key`() {
        val master = WrappingKey.generateWrappingKey(schemeMetadata)
        val keyPair = generateKeyPair(schemeMetadata, EDDSA_ED25519_CODE_NAME)
        val alias = "master-alias"
        val wrappingKeyMap = mock<SoftWrappingKeyMap> {
            on { getWrappingKey(alias) } doReturn master
        }
        val cut = DefaultSoftPrivateKeyWrapping(wrappingKeyMap)
        val keyMaterial = cut.wrap(keyPair.private, alias)
        assertEquals(PRIVATE_KEY_ENCODING_VERSION, keyMaterial.encodingVersion)
        assertEquals(keyPair.private, master.unwrap(keyMaterial.keyMaterial))
    }

    @Test
    fun `Should fail wrap if master key alias is null or empty`() {
        val keyPair = generateKeyPair(schemeMetadata, EDDSA_ED25519_CODE_NAME)
        val wrappingKeyMap = mock<SoftWrappingKeyMap>()
        val cut = DefaultSoftPrivateKeyWrapping(wrappingKeyMap)
        assertThrows<IllegalArgumentException> {
            cut.wrap(keyPair.private, null)
        }
        assertThrows<IllegalArgumentException> {
            cut.wrap(keyPair.private, "")
        }
    }
}