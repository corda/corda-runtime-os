package net.corda.crypto.service.impl.soft

import net.corda.crypto.service.impl.signing.CryptoServicesTestFactory
import net.corda.v5.cipher.suite.WrappedPrivateKey
import net.corda.v5.crypto.exceptions.CryptoServiceException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import java.util.UUID
import kotlin.test.assertSame

class SoftCryptoServiceExceptionsTests {
    @Test
    fun `Should re-throw same CryptoServiceException when failing containsKey`() {
        val exception = CryptoServiceException("")
        val service = SoftCryptoService(
            mock {
                on { find(any()) }.thenThrow(exception)
            },
            CryptoServicesTestFactory().schemeMetadata,
            mock()
        )
        val thrown = assertThrows<CryptoServiceException> {
            service.containsKey(UUID.randomUUID().toString())
        }
        assertSame(exception, thrown)
    }

    @Test
    fun `Should wrap in CryptoServiceException when failing containsKey`() {
        val exception = RuntimeException("")
        val service = SoftCryptoService(
            mock {
                on { find(any()) }.thenThrow(exception)
            },
            CryptoServicesTestFactory().schemeMetadata,
            mock()
        )
        val thrown = assertThrows<CryptoServiceException> {
            service.containsKey(UUID.randomUUID().toString())
        }
        assertSame(exception, thrown.cause)
    }

    @Test
    fun `Should re-throw same CryptoServiceException when failing findPublicKey`() {
        val exception = CryptoServiceException("")
        val service = SoftCryptoService(
            mock {
                on { find(any()) }.thenThrow(exception)
            },
            CryptoServicesTestFactory().schemeMetadata,
            mock()
        )
        val thrown = assertThrows<CryptoServiceException> {
            service.findPublicKey(UUID.randomUUID().toString())
        }
        assertSame(exception, thrown)
    }

    @Test
    fun `Should wrap in CryptoServiceException when failing findPublicKey`() {
        val exception = RuntimeException("")
        val service = SoftCryptoService(
            mock {
                on { find(any()) }.thenThrow(exception)
            },
            CryptoServicesTestFactory().schemeMetadata,
            mock()
        )
        val thrown = assertThrows<CryptoServiceException> {
            service.findPublicKey(UUID.randomUUID().toString())
        }
        assertSame(exception, thrown.cause)
    }

    @Test
    fun `Should re-throw same CryptoServiceException when failing createWrappingKey`() {
        val exception = CryptoServiceException("")
        val service = SoftCryptoService(
            mock {
                on { find(any()) }.thenThrow(exception)
            },
            CryptoServicesTestFactory().schemeMetadata,
            mock()
        )
        val thrown = assertThrows<CryptoServiceException> {
            service.createWrappingKey(UUID.randomUUID().toString(), false)
        }
        assertSame(exception, thrown)
    }

    @Test
    fun `Should wrap in CryptoServiceException when failing createWrappingKey`() {
        val exception = RuntimeException("")
        val service = SoftCryptoService(
            mock {
                on { find(any()) }.thenThrow(exception)
            },
            CryptoServicesTestFactory().schemeMetadata,
            mock()
        )
        val thrown = assertThrows<CryptoServiceException> {
            service.createWrappingKey(UUID.randomUUID().toString(), false)
        }
        assertSame(exception, thrown.cause)
    }

    @Test
    fun `Should re-throw same CryptoServiceException when failing signing with alias`() {
        val exception = CryptoServiceException("")
        val service = SoftCryptoService(
            mock {
                on { find(any()) }.thenThrow(exception)
            },
            CryptoServicesTestFactory().schemeMetadata,
            mock()
        )
        val thrown = assertThrows<CryptoServiceException> {
            service.sign(UUID.randomUUID().toString(), service.supportedSchemes()[0], ByteArray(2), emptyMap())
        }
        assertSame(exception, thrown)
    }

    @Test
    fun `Should wrap in CryptoServiceException when failing signing with alias`() {
        val exception = RuntimeException("")
        val service = SoftCryptoService(
            mock {
                on { find(any()) }.thenThrow(exception)
            },
            CryptoServicesTestFactory().schemeMetadata,
            mock()
        )
        val thrown = assertThrows<CryptoServiceException> {
            service.sign(UUID.randomUUID().toString(), service.supportedSchemes()[0], ByteArray(2), emptyMap())
        }
        assertSame(exception, thrown.cause)
    }

    @Test
    fun `Should re-throw same CryptoServiceException when failing signing with wrapped key`() {
        val exception = CryptoServiceException("")
        val service = SoftCryptoService(
            mock {
                on { find(any()) }.thenThrow(exception)
            },
            CryptoServicesTestFactory().schemeMetadata,
            mock()
        )
        val thrown = assertThrows<CryptoServiceException> {
            service.sign(
                WrappedPrivateKey(
                    keyMaterial = ByteArray(2),
                    masterKeyAlias = UUID.randomUUID().toString(),
                    signatureScheme = service.supportedWrappingSchemes()[0],
                    encodingVersion = 1
                ),
                ByteArray(2),
                emptyMap()
            )
        }
        assertSame(exception, thrown)
    }

    @Test
    fun `Should wrap in CryptoServiceException when failing signing wrapped key`() {
        val exception = RuntimeException("")
        val service = SoftCryptoService(
            mock {
                on { find(any()) }.thenThrow(exception)
            },
            CryptoServicesTestFactory().schemeMetadata,
            mock()
        )
        val thrown = assertThrows<CryptoServiceException> {
            service.sign(
                WrappedPrivateKey(
                    keyMaterial = ByteArray(2),
                    masterKeyAlias = UUID.randomUUID().toString(),
                    signatureScheme = service.supportedWrappingSchemes()[0],
                    encodingVersion = 1
                ),
                ByteArray(2),
                emptyMap()
            )
        }
        assertSame(exception, thrown.cause)
    }
}