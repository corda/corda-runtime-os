package net.corda.crypto.softhsm.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.cipher.suite.CRYPTO_CATEGORY
import net.corda.crypto.cipher.suite.CRYPTO_TENANT_ID
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.KeyGenerationSpec
import net.corda.crypto.cipher.suite.KeyMaterialSpec
import net.corda.crypto.cipher.suite.SharedSecretWrappedSpec
import net.corda.crypto.cipher.suite.SigningWrappedSpec
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.component.test.utils.generateKeyPair
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.impl.CipherSchemeMetadataProvider
import net.corda.crypto.softhsm.PRIVATE_KEY_ENCODING_VERSION
import net.corda.crypto.softhsm.SoftWrappingKeyMap
import net.corda.v5.crypto.CordaOID
import net.corda.v5.crypto.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.EDDSA_ED25519_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.X25519_CODE_NAME
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import java.util.UUID

val OID_COMPOSITE_KEY_IDENTIFIER = ASN1ObjectIdentifier(CordaOID.OID_COMPOSITE_KEY)

class SoftCryptoServiceGeneralTests {
    companion object {
        private lateinit var schemeMetadata: CipherSchemeMetadata
        private lateinit var UNSUPPORTED_SIGNATURE_SCHEME: KeyScheme

        @BeforeAll
        @JvmStatic
        fun setup() {
            schemeMetadata = CipherSchemeMetadataImpl()
            UNSUPPORTED_SIGNATURE_SCHEME = CipherSchemeMetadataProvider().COMPOSITE_KEY_TEMPLATE.makeScheme("BC")
        }
    }

    @Test
    fun `Should throw IllegalStateException when master alias exists and failIfExists is true`() {
        val wrappingKeyMap = mock<SoftWrappingKeyMap> {
            on { exists(any()) } doReturn true
        }
        val service = SoftCryptoService(
            mock(),
            wrappingKeyMap,
            schemeMetadata,
            mock()
        )
        assertThrows<IllegalStateException> {
            service.createWrappingKey(UUID.randomUUID().toString(), true, emptyMap())
        }
        Mockito.verify(wrappingKeyMap, never()).putWrappingKey(any(), any())
    }

    @Test
    fun `Should not generate new master key when master alias exists and failIfExists is false`() {
        val wrappingKeyMap = mock<SoftWrappingKeyMap> {
            on { exists(any()) } doReturn true
        }
        val service = SoftCryptoService(
            mock(),
            wrappingKeyMap,
            schemeMetadata,
            mock()
        )
        service.createWrappingKey(UUID.randomUUID().toString(), false, emptyMap())
        Mockito.verify(wrappingKeyMap, never()).putWrappingKey(any(), any())
    }

    @Test
    @Suppress("MaxLineLength")
    fun `Should throw IllegalArgumentException when generating key pair and signature scheme is not supported`() {
        val service = SoftCryptoService(
            mock(),
            mock(),
            schemeMetadata,
            mock()
        )
        assertThrows<IllegalArgumentException> {
            service.generateKeyPair(
                KeyGenerationSpec(
                    alias = UUID.randomUUID().toString(),
                    masterKeyAlias = UUID.randomUUID().toString(),
                    keyScheme = UNSUPPORTED_SIGNATURE_SCHEME
                ),
                mapOf(
                    CRYPTO_TENANT_ID to UUID.randomUUID().toString(),
                    CRYPTO_CATEGORY to CryptoConsts.Categories.LEDGER
                )
            )
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when signing and spec is not SigningWrappedSpec`() {
        val service = SoftCryptoService(
            mock(),
            mock(),
            schemeMetadata,
            mock()
        )
        assertThrows<IllegalArgumentException> {
            service.sign(
                mock(),
                ByteArray(2),
                mapOf(
                    CRYPTO_TENANT_ID to UUID.randomUUID().toString()
                )
            )
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when signing empty data array`() {
        val service = SoftCryptoService(
            mock(),
            mock(),
            schemeMetadata,
            mock()
        )
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
                mapOf(
                    CRYPTO_TENANT_ID to UUID.randomUUID().toString()
                )
            )
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when signing using unsupported scheme`() {
        val service = SoftCryptoService(
            mock(),
            mock(),
            schemeMetadata,
            mock()
        )
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
                mapOf(
                    CRYPTO_TENANT_ID to UUID.randomUUID().toString()
                )
            )
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when signing using scheme which does not support signing`() {
        val service = SoftCryptoService(
            mock(),
            mock(),
            schemeMetadata,
            mock()
        )
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
                mapOf(
                    CRYPTO_TENANT_ID to UUID.randomUUID().toString()
                )
            )
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when deriving key and spec is not SharedSecretWrappedSpec`() {
        val service = SoftCryptoService(
            mock(),
            mock(),
            schemeMetadata,
            mock()
        )
        assertThrows<IllegalArgumentException> {
            service.deriveSharedSecret(
                mock(),
                mapOf(
                    CRYPTO_TENANT_ID to UUID.randomUUID().toString()
                )
            )
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when deriving key using scheme which does not support it`() {
        val service = SoftCryptoService(
            mock(),
            mock(),
            schemeMetadata,
            mock()
        )
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
                mapOf(
                    CRYPTO_TENANT_ID to UUID.randomUUID().toString()
                )
            )
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when deriving key using keys with different schemes`() {
        val service = SoftCryptoService(
            mock(),
            mock(),
            schemeMetadata,
            mock()
        )
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
                mapOf(
                    CRYPTO_TENANT_ID to UUID.randomUUID().toString()
                )
            )
        }
    }
}