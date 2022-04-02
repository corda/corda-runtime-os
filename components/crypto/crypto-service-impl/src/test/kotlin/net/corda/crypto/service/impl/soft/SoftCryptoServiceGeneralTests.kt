package net.corda.crypto.service.impl.soft

import net.corda.crypto.impl.components.CipherSchemeMetadataImpl
import net.corda.crypto.persistence.SigningKeyCacheActions
import net.corda.crypto.persistence.SoftCryptoKeyCache
import net.corda.crypto.persistence.SoftCryptoKeyCacheActions
import net.corda.crypto.persistence.WrappingKey
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.SigningWrappedSpec
import net.corda.v5.cipher.suite.schemes.COMPOSITE_KEY_TEMPLATE
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.corda.v5.crypto.exceptions.CryptoServiceException
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import java.util.UUID
import kotlin.test.assertSame

class SoftCryptoServiceGeneralTests {
    companion object {
        private lateinit var schemeMetadata: CipherSchemeMetadata
        private lateinit var UNSUPPORTED_SIGNATURE_SCHEME: SignatureScheme

        @BeforeAll
        @JvmStatic
        fun setup() {
            schemeMetadata = CipherSchemeMetadataImpl()
            UNSUPPORTED_SIGNATURE_SCHEME = COMPOSITE_KEY_TEMPLATE.makeScheme("BC")
        }
    }

    @Test
    fun `Should throw CryptoServiceBadRequestException when master alias exists and failIfExists is true`() {
        val cacheActions = mock<SoftCryptoKeyCacheActions> {
            on { findWrappingKey(any()) } doReturn WrappingKey.createWrappingKey(schemeMetadata)
        }
        val service = SoftCryptoService(
            mock {
                on { act() } doReturn cacheActions
                on { act<SoftCryptoKeyCacheActions>(any()) }.thenCallRealMethod()
            },
            schemeMetadata,
            mock()
        )
        assertThrows<CryptoServiceBadRequestException> {
            service.createWrappingKey(UUID.randomUUID().toString(), true, emptyMap())
        }
        Mockito.verify(cacheActions, never()).saveWrappingKey(any(), any())
    }

    @Test
    fun `Should not generate new master key when master alias exists and failIfExists is false`() {
        val cacheActions = mock<SoftCryptoKeyCacheActions> {
            on { findWrappingKey(any()) } doReturn WrappingKey.createWrappingKey(schemeMetadata)
        }
        val service = SoftCryptoService(
            mock {
                on { act() } doReturn cacheActions
                on { act<SoftCryptoKeyCacheActions>(any()) }.thenCallRealMethod()
            },
            schemeMetadata,
            mock()
        )
        service.createWrappingKey(UUID.randomUUID().toString(), false, emptyMap())
        Mockito.verify(cacheActions, never()).saveWrappingKey(any(), any())
    }

    @Test
    fun `Should re-throw same CryptoServiceException when failing createWrappingKey`() {
        val exception = CryptoServiceException("")
        val cache = mock<SoftCryptoKeyCache> {
            on { act<SoftCryptoKeyCacheActions>(any()) } doThrow exception
        }
        val service = SoftCryptoService(
            cache,
            schemeMetadata,
            mock()
        )
        val thrown = assertThrows<CryptoServiceException> {
            service.createWrappingKey(UUID.randomUUID().toString(), false, emptyMap())
        }
        assertSame(exception, thrown)
        Mockito.verify(cache, times(1)).act<SigningKeyCacheActions>(any())
    }

    @Test
    fun `Should wrap in CryptoServiceException when failing createWrappingKey`() {
        val exception = RuntimeException("")
        val cache = mock<SoftCryptoKeyCache> {
            on { act<SoftCryptoKeyCacheActions>(any()) } doThrow exception
        }
        val service = SoftCryptoService(
            cache,
            schemeMetadata,
            mock()
        )
        val thrown = assertThrows<CryptoServiceException> {
            service.createWrappingKey(UUID.randomUUID().toString(), false, emptyMap())
        }
        assertSame(exception, thrown.cause)
        Mockito.verify(cache, times(1)).act<SigningKeyCacheActions>(any())
    }

    @Test
    fun `Should throw CryptoServiceBadRequestException when generating key pair and masterKeyAlias is null`() {
        val service = SoftCryptoService(
            mock(),
            schemeMetadata,
            mock()
        )
        assertThrows<CryptoServiceBadRequestException> {
            service.generateKeyPair(
                KeyGenerationSpec(
                    tenantId = UUID.randomUUID().toString(),
                    alias = UUID.randomUUID().toString(),
                    masterKeyAlias = null,
                    signatureScheme = service.supportedSchemes()[0],
                    secret = null
                ),
                emptyMap()
            )
        }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `Should throw CryptoServiceBadRequestException when generating key pair and signature scheme is not supported`() {
        val service = SoftCryptoService(
            mock(),
            schemeMetadata,
            mock()
        )
        assertThrows<CryptoServiceBadRequestException> {
            service.generateKeyPair(
                KeyGenerationSpec(
                    tenantId = UUID.randomUUID().toString(),
                    alias = UUID.randomUUID().toString(),
                    masterKeyAlias = UUID.randomUUID().toString(),
                    signatureScheme = UNSUPPORTED_SIGNATURE_SCHEME,
                    secret = null
                ),
                emptyMap()
            )
        }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `Should throw CryptoServiceBadRequestException when generating key pair and wrapping key is not generated yet`() {
        val service = SoftCryptoService(
            mock(),
            schemeMetadata,
            mock()
        )
        assertThrows<CryptoServiceBadRequestException> {
            service.generateKeyPair(
                KeyGenerationSpec(
                    tenantId = UUID.randomUUID().toString(),
                    alias = UUID.randomUUID().toString(),
                    masterKeyAlias = UUID.randomUUID().toString(),
                    signatureScheme = service.supportedSchemes()[0],
                    secret = null
                ),
                emptyMap()
            )
        }
    }

    @Test
    fun `Should re-throw same CryptoServiceException when failing generating key pair`() {
        val exception = CryptoServiceException("")
        val cache = mock<SoftCryptoKeyCache> {
            on { act<SoftCryptoKeyCacheActions>(any()) } doThrow exception
        }
        val service = SoftCryptoService(
            cache,
            schemeMetadata,
            mock()
        )
        val thrown = assertThrows<CryptoServiceException> {
            service.generateKeyPair(
                KeyGenerationSpec(
                    tenantId = UUID.randomUUID().toString(),
                    alias = UUID.randomUUID().toString(),
                    masterKeyAlias = UUID.randomUUID().toString(),
                    signatureScheme = service.supportedSchemes()[0],
                    secret = null
                ),
                emptyMap()
            )
        }
        assertSame(exception, thrown)
        Mockito.verify(cache, times(1)).act<SigningKeyCacheActions>(any())
    }

    @Test
    fun `Should wrap in CryptoServiceException when failing generating key pair`() {
        val exception = RuntimeException("")
        val cache = mock<SoftCryptoKeyCache> {
            on { act<SoftCryptoKeyCacheActions>(any()) } doThrow exception
        }
        val service = SoftCryptoService(
            cache,
            schemeMetadata,
            mock()
        )
        val thrown = assertThrows<CryptoServiceException> {
            service.generateKeyPair(
                KeyGenerationSpec(
                    tenantId = UUID.randomUUID().toString(),
                    alias = UUID.randomUUID().toString(),
                    masterKeyAlias = UUID.randomUUID().toString(),
                    signatureScheme = service.supportedSchemes()[0],
                    secret = null
                ),
                emptyMap()
            )
        }
        assertSame(exception, thrown.cause)
        Mockito.verify(cache, times(1)).act<SigningKeyCacheActions>(any())
    }

    @Test
    fun `Should throw CryptoServiceBadRequestException when signing and masterKeyAlias is null`() {
        val service = SoftCryptoService(
            mock(),
            schemeMetadata,
            mock()
        )
        assertThrows<CryptoServiceBadRequestException> {
            service.sign(
                SigningWrappedSpec(
                    tenantId = UUID.randomUUID().toString(),
                    keyMaterial = ByteArray(2),
                    masterKeyAlias = null,
                    encodingVersion = 1,
                    signatureScheme = service.supportedSchemes()[0]
                ),
                ByteArray(2),
                emptyMap()
            )
        }
    }

    @Test
    fun `Should throw CryptoServiceBadRequestException when signing and spec is not SigningWrappedSpec`() {
        val service = SoftCryptoService(
            mock(),
            schemeMetadata,
            mock()
        )
        assertThrows<CryptoServiceBadRequestException> {
            service.sign(
                mock(),
                ByteArray(2),
                emptyMap()
            )
        }
    }

    @Test
    fun `Should throw CryptoServiceBadRequestException when signing and masterKeyAlias is not exists yet`() {
        val service = SoftCryptoService(
            mock(),
            schemeMetadata,
            mock()
        )
        assertThrows<CryptoServiceBadRequestException> {
            service.sign(
                SigningWrappedSpec(
                    tenantId = UUID.randomUUID().toString(),
                    keyMaterial = ByteArray(2),
                    masterKeyAlias = UUID.randomUUID().toString(),
                    encodingVersion = 1,
                    signatureScheme = service.supportedSchemes()[0]
                ),
                ByteArray(2),
                emptyMap()
            )
        }
    }

    @Test
    fun `Should re-throw same CryptoServiceException when failing signing`() {
        val exception = CryptoServiceException("")
        val cache = mock<SoftCryptoKeyCache> {
            on { act<SoftCryptoKeyCacheActions>(any()) } doThrow exception
        }
        val service = SoftCryptoService(
            cache,
            schemeMetadata,
            mock()
        )
        val thrown = assertThrows<CryptoServiceException> {
            service.sign(
                SigningWrappedSpec(
                    tenantId = UUID.randomUUID().toString(),
                    keyMaterial = ByteArray(2),
                    masterKeyAlias = UUID.randomUUID().toString(),
                    encodingVersion = 1,
                    signatureScheme = service.supportedSchemes()[0]
                ),
                ByteArray(2),
                emptyMap()
            )
        }
        assertSame(exception, thrown)
        Mockito.verify(cache, times(1)).act<SigningKeyCacheActions>(any())
    }

    @Test
    fun `Should wrap in CryptoServiceException when failing signing`() {
        val exception = RuntimeException("")
        val cache = mock<SoftCryptoKeyCache> {
            on { act<SoftCryptoKeyCacheActions>(any()) } doThrow exception
        }
        val service = SoftCryptoService(
            cache,
            schemeMetadata,
            mock()
        )
        val thrown = assertThrows<CryptoServiceException> {
            service.sign(
                SigningWrappedSpec(
                    tenantId = UUID.randomUUID().toString(),
                    keyMaterial = ByteArray(2),
                    masterKeyAlias = UUID.randomUUID().toString(),
                    encodingVersion = 1,
                    signatureScheme = service.supportedSchemes()[0]
                ),
                ByteArray(2),
                emptyMap()
            )
        }
        assertSame(exception, thrown.cause)
        Mockito.verify(cache, times(1)).act<SigningKeyCacheActions>(any())
    }
}