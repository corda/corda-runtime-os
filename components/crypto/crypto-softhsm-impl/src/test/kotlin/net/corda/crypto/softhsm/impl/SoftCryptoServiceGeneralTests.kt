package net.corda.crypto.softhsm.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.crypto.cipher.suite.CRYPTO_CATEGORY
import net.corda.crypto.cipher.suite.CRYPTO_TENANT_ID
import net.corda.crypto.cipher.suite.CryptoServiceExtensions
import net.corda.crypto.cipher.suite.KeyGenerationSpec
import net.corda.crypto.cipher.suite.KeyMaterialSpec
import net.corda.crypto.cipher.suite.SharedSecretWrappedSpec
import net.corda.crypto.cipher.suite.SigningWrappedSpec
import net.corda.crypto.component.test.utils.generateKeyPair
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.impl.CipherSchemeMetadataProvider
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.impl.infra.TestWrappingKeyStore
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.KeySchemeCodes.X25519_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.assertj.core.api.Assertions.assertThat

import java.util.UUID
import kotlin.test.assertTrue

/* SoftCryptoService tests that do not require wrapping keys */
class SoftCryptoServiceGeneralTests {
    private val schemeMetadata = CipherSchemeMetadataImpl()
    private val UNSUPPORTED_SIGNATURE_SCHEME = CipherSchemeMetadataProvider().COMPOSITE_KEY_TEMPLATE.makeScheme("BC")
    private val wrappingKeyStore = TestWrappingKeyStore(mock())
    private val sampleWrappingKeyInfo = WrappingKeyInfo(1, "n", byteArrayOf())
    val defaultContext =
        mapOf(CRYPTO_TENANT_ID to UUID.randomUUID().toString(), CRYPTO_CATEGORY to CryptoConsts.Categories.LEDGER)
    private val service = SoftCryptoService(
        wrappingKeyStore = wrappingKeyStore,
        schemeMetadata = schemeMetadata,
        rootWrappingKey = mock(),
        digestService = PlatformDigestServiceImpl(schemeMetadata)
    )

    @Test
    fun `Should throw IllegalStateException when wrapping key alias exists and failIfExists is true`() {
        val alias = "stuff"
        wrappingKeyStore.keys[alias] = sampleWrappingKeyInfo
        assertThrows<IllegalStateException> {
            service.createWrappingKey(alias, true, emptyMap())
        }
        assertThat(wrappingKeyStore.keys[alias]).isEqualTo(sampleWrappingKeyInfo)
    }

    @Test
    fun `Should not generate new master key when master alias exists and failIfExists is false`() {
        wrappingKeyStore.keys["stuff2"] = sampleWrappingKeyInfo
        service.createWrappingKey("stuff2", false, emptyMap())
        assertThat(wrappingKeyStore.keys["stuff2"]).isEqualTo(sampleWrappingKeyInfo)
    }

    @Test
    @Suppress("MaxLineLength")
    fun `Should throw IllegalArgumentException when generating key pair and signature scheme is not supported`() {
        wrappingKeyStore.keys["stuff3"] = sampleWrappingKeyInfo
        assertThrows<IllegalArgumentException> {
            service.generateKeyPair(
                KeyGenerationSpec(
                    keyScheme = UNSUPPORTED_SIGNATURE_SCHEME,
                    alias = "whatever",
                    masterKeyAlias = "stuff3",
                ),
                defaultContext
            )
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when signing and spec is not SigningWrappedSpec`() {
        assertThrows<IllegalArgumentException> {
            service.sign(mock(), ByteArray(2), defaultContext)
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when signing empty data array`() {
        assertThrows<IllegalArgumentException> {
            service.sign(
                SigningWrappedSpec(
                    publicKey = mock(),
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = ByteArray(2),
                        masterKeyAlias = UUID.randomUUID().toString(),
                        encodingVersion = PRIVATE_KEY_ENCODING_VERSION
                    ),
                    keyScheme = service.supportedSchemes.keys.first { it.codeName == ECDSA_SECP256R1_CODE_NAME },
                    signatureSpec = SignatureSpec.ECDSA_SHA256
                ),
                ByteArray(0),
                defaultContext
            )
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when signing using unsupported scheme`() {
        assertThrows<IllegalArgumentException> {
            service.sign(
                SigningWrappedSpec(
                    publicKey = mock(),
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = ByteArray(2),
                        masterKeyAlias = UUID.randomUUID().toString(),
                        encodingVersion = PRIVATE_KEY_ENCODING_VERSION
                    ),
                    keyScheme = UNSUPPORTED_SIGNATURE_SCHEME,
                    signatureSpec = SignatureSpec.ECDSA_SHA256
                ),
                ByteArray(0),
                defaultContext
            )
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when signing using scheme which does not support signing`() {
        assertThrows<IllegalArgumentException> {
            service.sign(
                SigningWrappedSpec(
                    publicKey = mock(),
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = ByteArray(2),
                        masterKeyAlias = UUID.randomUUID().toString(),
                        encodingVersion = PRIVATE_KEY_ENCODING_VERSION
                    ),
                    keyScheme = service.supportedSchemes.keys.first { it.codeName == X25519_CODE_NAME },
                    signatureSpec = SignatureSpec.EDDSA_ED25519
                ),
                ByteArray(0),
                defaultContext
            )
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when deriving key and spec is not SharedSecretWrappedSpec`() {
        assertThrows<IllegalArgumentException> {
            service.deriveSharedSecret(mock(), defaultContext)
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when deriving key using scheme which does not support it`() {
        val keyScheme = service.supportedSchemes.keys.first { it.codeName == EDDSA_ED25519_CODE_NAME }
        val myKeyPair = generateKeyPair(schemeMetadata, EDDSA_ED25519_CODE_NAME)
        val otherKeyPair = generateKeyPair(schemeMetadata, EDDSA_ED25519_CODE_NAME)
        assertThrows<IllegalArgumentException> {
            service.deriveSharedSecret(
                SharedSecretWrappedSpec(
                    publicKey = myKeyPair.public,
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = ByteArray(2),
                        masterKeyAlias = UUID.randomUUID().toString(),
                        encodingVersion = PRIVATE_KEY_ENCODING_VERSION
                    ),
                    keyScheme = keyScheme,
                    otherPublicKey = otherKeyPair.public
                ),
                defaultContext
            )
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when deriving key using keys with different schemes`() {
        val keyScheme = service.supportedSchemes.keys.first { it.codeName == ECDSA_SECP256R1_CODE_NAME }
        val myKeyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME)
        val otherKeyPair = generateKeyPair(schemeMetadata, ECDSA_SECP256K1_CODE_NAME)
        assertThrows<IllegalArgumentException> {
            service.deriveSharedSecret(
                SharedSecretWrappedSpec(
                    publicKey = myKeyPair.public,
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = ByteArray(2),
                        masterKeyAlias = UUID.randomUUID().toString(),
                        encodingVersion = PRIVATE_KEY_ENCODING_VERSION
                    ),
                    keyScheme = keyScheme,
                    otherPublicKey = otherKeyPair.public
                ),
                defaultContext
            )
        }
    }

    @Test
    fun `SoftCryptoService should require wrapping key`() {
        assertThat(service.extensions).contains(CryptoServiceExtensions.REQUIRE_WRAPPING_KEY)
    }


    @Test
    fun `SoftCryptoService should not support key deletion`() {
        assertThat(service.extensions).doesNotContain(CryptoServiceExtensions.DELETE_KEYS)
    }


    @Test
    fun `SoftCryptoService should support at least one schemes defined in cipher suite`() {
        assertThat(service.supportedSchemes).isNotEmpty
        assertTrue(service.supportedSchemes.any {
            schemeMetadata.schemes.contains(it.key)
        })
    }
}