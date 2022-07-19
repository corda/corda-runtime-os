package net.corda.crypto.service.impl.hsm.soft

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.component.test.utils.generateKeyPair
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.persistence.soft.SoftCryptoKeyStoreActions
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.service.impl.softhsm.SoftCryptoService
import net.corda.v5.cipher.suite.CRYPTO_CATEGORY
import net.corda.v5.cipher.suite.CRYPTO_TENANT_ID
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.KeyMaterialSpec
import net.corda.v5.cipher.suite.SharedSecretWrappedSpec
import net.corda.v5.cipher.suite.SigningWrappedSpec
import net.corda.v5.cipher.suite.schemes.COMPOSITE_KEY_TEMPLATE
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.X25519_CODE_NAME
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import java.util.UUID

class SoftCryptoServiceGeneralTests {
    companion object {
        private lateinit var schemeMetadata: CipherSchemeMetadata
        private lateinit var UNSUPPORTED_SIGNATURE_SCHEME: KeyScheme

        @BeforeAll
        @JvmStatic
        fun setup() {
            schemeMetadata = CipherSchemeMetadataImpl()
            UNSUPPORTED_SIGNATURE_SCHEME = COMPOSITE_KEY_TEMPLATE.makeScheme("BC")
        }
    }

    @Test
    fun `Should throw IllegalStateException when master alias exists and failIfExists is true`() {
        val actions = mock<SoftCryptoKeyStoreActions> {
            on { findWrappingKey(any()) } doReturn WrappingKey.generateWrappingKey(schemeMetadata)
        }
        val service = SoftCryptoService(
            mock {
                on { act() } doReturn actions
                on { act<SoftCryptoKeyStoreActions>(any()) }.thenCallRealMethod()
            },
            schemeMetadata,
            mock()
        )
        assertThrows<IllegalStateException> {
            service.createWrappingKey(UUID.randomUUID().toString(), true, emptyMap())
        }
        Mockito.verify(actions, never()).saveWrappingKey(any(), any(), any())
    }

    @Test
    fun `Should not generate new master key when master alias exists and failIfExists is false`() {
        val actions = mock<SoftCryptoKeyStoreActions> {
            on { findWrappingKey(any()) } doReturn WrappingKey.generateWrappingKey(schemeMetadata)
        }
        val service = SoftCryptoService(
            mock {
                on { act() } doReturn actions
                on { act<SoftCryptoKeyStoreActions>(any()) }.thenCallRealMethod()
            },
            schemeMetadata,
            mock()
        )
        service.createWrappingKey(UUID.randomUUID().toString(), false, emptyMap())
        Mockito.verify(actions, never()).saveWrappingKey(any(), any(), any())
    }

    @Test
    fun `Should throw IllegalArgumentException when generating key pair and masterKeyAlias is null`() {
        val service = SoftCryptoService(
            mock(),
            schemeMetadata,
            mock()
        )
        assertThrows<IllegalArgumentException> {
            service.generateKeyPair(
                KeyGenerationSpec(
                    alias = UUID.randomUUID().toString(),
                    masterKeyAlias = null,
                    keyScheme = service.supportedSchemes.keys.first(),
                    secret = null
                ),
                mapOf(
                    CRYPTO_TENANT_ID to UUID.randomUUID().toString(),
                    CRYPTO_CATEGORY to CryptoConsts.Categories.LEDGER
                )
            )
        }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `Should throw IllegalArgumentException when generating key pair and signature scheme is not supported`() {
        val service = SoftCryptoService(
            mock(),
            schemeMetadata,
            mock()
        )
        assertThrows<IllegalArgumentException> {
            service.generateKeyPair(
                KeyGenerationSpec(
                    alias = UUID.randomUUID().toString(),
                    masterKeyAlias = UUID.randomUUID().toString(),
                    keyScheme = UNSUPPORTED_SIGNATURE_SCHEME,
                    secret = null
                ),
                mapOf(
                    CRYPTO_TENANT_ID to UUID.randomUUID().toString(),
                    CRYPTO_CATEGORY to CryptoConsts.Categories.LEDGER
                )
            )
        }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `Should throw IllegalStateException when generating key pair and wrapping key is not generated yet`() {
        val service = SoftCryptoService(
            mock(),
            schemeMetadata,
            mock()
        )
        assertThrows<IllegalStateException> {
            service.generateKeyPair(
                KeyGenerationSpec(
                    alias = UUID.randomUUID().toString(),
                    masterKeyAlias = UUID.randomUUID().toString(),
                    keyScheme = service.supportedSchemes.keys.first(),
                    secret = null
                ),
                mapOf(
                    CRYPTO_TENANT_ID to UUID.randomUUID().toString(),
                    CRYPTO_CATEGORY to CryptoConsts.Categories.LEDGER
                )
            )
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when signing and masterKeyAlias is null`() {
        val service = SoftCryptoService(
            mock(),
            schemeMetadata,
            mock()
        )
        val scheme = service.supportedSchemes.keys.first()
        val signatureSpec = schemeMetadata.supportedSignatureSpec(scheme).first()
        assertThrows<IllegalArgumentException> {
            service.sign(
                SigningWrappedSpec(
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = ByteArray(2),
                        masterKeyAlias = null,
                        encodingVersion = 1
                    ),
                    keyScheme = scheme,
                    signatureSpec = signatureSpec
                ),
                ByteArray(2),
                mapOf(
                    CRYPTO_TENANT_ID to UUID.randomUUID().toString()
                )
            )
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when signing and spec is not SigningWrappedSpec`() {
        val service = SoftCryptoService(
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
    fun `Should throw IllegalStateException when signing and masterKeyAlias does not exist yet`() {
        val service = SoftCryptoService(
            mock(),
            schemeMetadata,
            mock()
        )
        val scheme = service.supportedSchemes.keys.first()
        val signatureSpec = schemeMetadata.supportedSignatureSpec(scheme).first()
        assertThrows<IllegalStateException> {
            service.sign(
                SigningWrappedSpec(
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = ByteArray(2),
                        masterKeyAlias = UUID.randomUUID().toString(),
                        encodingVersion = 1
                    ),
                    keyScheme = scheme,
                    signatureSpec = signatureSpec
                ),
                ByteArray(2),
                mapOf(
                    CRYPTO_TENANT_ID to UUID.randomUUID().toString()
                )
            )
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when deriving key and masterKeyAlias is null`() {
        val service = SoftCryptoService(
            mock(),
            schemeMetadata,
            mock()
        )
        val scheme = service.supportedSchemes.keys.first { it.codeName == X25519_CODE_NAME }
        val keyPair = generateKeyPair(schemeMetadata, scheme.codeName)
        val otherPublicKey = generateKeyPair(schemeMetadata, scheme.codeName).public
        assertThrows<IllegalArgumentException> {
            service.deriveSharedSecret(
                SharedSecretWrappedSpec(
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = keyPair.private.encoded,
                        masterKeyAlias = null,
                        encodingVersion = 1
                    ),
                    keyScheme = scheme,
                    otherPublicKey = otherPublicKey
                ),
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
    fun `Should throw IllegalStateException when deriving key and masterKeyAlias does not exist yet`() {
        val service = SoftCryptoService(
            mock(),
            schemeMetadata,
            mock()
        )
        val scheme = service.supportedSchemes.keys.first { it.codeName == X25519_CODE_NAME }
        val keyPair = generateKeyPair(schemeMetadata, scheme.codeName)
        val otherPublicKey = generateKeyPair(schemeMetadata, scheme.codeName).public
        assertThrows<IllegalStateException> {
            service.deriveSharedSecret(
                SharedSecretWrappedSpec(
                    keyMaterialSpec = KeyMaterialSpec(
                        keyMaterial = keyPair.private.encoded,
                        masterKeyAlias = UUID.randomUUID().toString(),
                        encodingVersion = 1
                    ),
                    keyScheme = scheme,
                    otherPublicKey = otherPublicKey
                ),
                mapOf(
                    CRYPTO_TENANT_ID to UUID.randomUUID().toString()
                )
            )
        }
    }
}