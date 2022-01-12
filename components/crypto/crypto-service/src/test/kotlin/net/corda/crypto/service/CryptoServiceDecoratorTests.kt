package net.corda.crypto.service

import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.WrappedKeyPair
import net.corda.v5.cipher.suite.WrappedPrivateKey
import net.corda.v5.cipher.suite.schemes.RSA_SHA256_TEMPLATE
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.exceptions.CryptoServiceException
import net.corda.v5.crypto.exceptions.CryptoServiceTimeoutException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.internal.stubbing.answers.AnswersWithDelay
import org.mockito.internal.stubbing.answers.Returns
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CryptoServiceDecoratorTests {

    private interface CryptoServiceStub: CryptoService, AutoCloseable

    private lateinit var cryptoService: CryptoServiceStub

    @BeforeEach
    fun setup() {
        cryptoService = mock()
    }

    @Test
    @Timeout(5)
    fun `Should close wrapped service`() {
        val circuitBreaker = createCircuitBreaker()
        circuitBreaker.close()
        Mockito.verify(cryptoService, times(1)).close()
    }

    @Test
    @Timeout(5)
    fun `Should execute requiresWrappingKey`() {
        val circuitBreaker = createCircuitBreaker()
        whenever(
            cryptoService.requiresWrappingKey()
        ).thenReturn(true, false)
        assertTrue(circuitBreaker.requiresWrappingKey())
        assertFalse(circuitBreaker.requiresWrappingKey())
    }

    @Test
    @Timeout(5)
    fun `Should throw same CryptoServiceException from wrapped service when executing requiresWrappingKey`() {
        val circuitBreaker = createCircuitBreaker()
        val expected = CryptoServiceException("")
        whenever(
            cryptoService.requiresWrappingKey()
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.requiresWrappingKey()
        }
        assertSame(expected, actual)
    }

    @Test
    @Timeout(5)
    fun `Should throw exception wrapped in CryptoServiceException when executing requiresWrappingKey`() {
        val circuitBreaker = createCircuitBreaker()
        val expected = RuntimeException()
        whenever(
            cryptoService.requiresWrappingKey()
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.requiresWrappingKey()
        }
        assertSame(expected, actual.cause)
    }

    @Test
    @Timeout(5)
    fun `Should execute supportedSchemes`() {
        val circuitBreaker = createCircuitBreaker()
        val expected = arrayOf<SignatureScheme>()
        whenever(
            cryptoService.supportedSchemes()
        ).thenReturn(expected)
        assertSame(expected, circuitBreaker.supportedSchemes())
    }

    @Test
    @Timeout(5)
    fun `Should throw same CryptoServiceException from wrapped service when executing supportedSchemes`() {
        val circuitBreaker = createCircuitBreaker()
        val expected = CryptoServiceException("")
        whenever(
            cryptoService.supportedSchemes()
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.supportedSchemes()
        }
        assertSame(expected, actual)
    }

    @Test
    @Timeout(5)
    fun `Should throw exception wrapped in CryptoServiceException  when executing supportedSchemes`() {
        val circuitBreaker = createCircuitBreaker()
        val expected = RuntimeException()
        whenever(
            cryptoService.supportedSchemes()
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.supportedSchemes()
        }
        assertSame(expected, actual.cause)
    }

    @Test
    @Timeout(5)
    fun `Should execute supportedWrappingSchemes`() {
        val circuitBreaker = createCircuitBreaker()
        val expected = arrayOf<SignatureScheme>()
        whenever(
            cryptoService.supportedWrappingSchemes()
        ).thenReturn(expected)
        assertSame(expected, circuitBreaker.supportedWrappingSchemes())
    }

    @Test
    @Timeout(5)
    fun `Should throw same CryptoServiceException from wrapped service when executing supportedWrappingSchemes`() {
        val circuitBreaker = createCircuitBreaker()
        val expected = CryptoServiceException("")
        whenever(
            cryptoService.supportedWrappingSchemes()
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.supportedWrappingSchemes()
        }
        assertSame(expected, actual)
    }

    @Test
    @Timeout(5)
    fun `Should throw exception wrapped in CryptoServiceException  when executing supportedWrappingSchemes`() {
        val circuitBreaker = createCircuitBreaker()
        val expected = RuntimeException()
        whenever(
            cryptoService.supportedWrappingSchemes()
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.supportedWrappingSchemes()
        }
        assertSame(expected, actual.cause)
    }

    @Test
    @Timeout(5)
    fun `Should execute containsKey`() {
        val circuitBreaker = createCircuitBreaker()
        val alias = UUID.randomUUID().toString()
        whenever(
            cryptoService.containsKey(alias)
        ).thenReturn(true, false)
        assertTrue(circuitBreaker.containsKey(alias))
        assertFalse(circuitBreaker.containsKey(alias))
    }

    @Test
    @Timeout(5)
    fun `Should throw same CryptoServiceException from wrapped service when executing containsKey`() {
        val circuitBreaker = createCircuitBreaker()
        val alias = UUID.randomUUID().toString()
        val expected = CryptoServiceException("")
        whenever(
            cryptoService.containsKey(alias)
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.containsKey(alias)
        }
        assertSame(expected, actual)
    }

    @Test
    @Timeout(5)
    fun `Should throw exception wrapped in CryptoServiceException when executing containsKey`() {
        val circuitBreaker = createCircuitBreaker()
        val alias = UUID.randomUUID().toString()
        val expected = RuntimeException()
        whenever(
            cryptoService.containsKey(alias)
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.containsKey(alias)
        }
        assertSame(expected, actual.cause)
    }

    @Test
    @Timeout(5)
    fun `Should execute findPublicKey`() {
        val circuitBreaker = createCircuitBreaker()
        val alias = UUID.randomUUID().toString()
        val expected = mock<PublicKey>()
        whenever(
            cryptoService.findPublicKey(alias)
        ).thenReturn(expected)
        assertSame(expected, circuitBreaker.findPublicKey(alias))
    }

    @Test
    @Timeout(5)
    fun `Should throw same CryptoServiceException from wrapped service when executing findPublicKey`() {
        val circuitBreaker = createCircuitBreaker()
        val alias = UUID.randomUUID().toString()
        val expected = CryptoServiceException("")
        whenever(
            cryptoService.findPublicKey(alias)
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.findPublicKey(alias)
        }
        assertSame(expected, actual)
    }

    @Test
    @Timeout(5)
    fun `Should throw exception wrapped in CryptoServiceException when executing findPublicKey`() {
        val circuitBreaker = createCircuitBreaker()
        val alias = UUID.randomUUID().toString()
        val expected = RuntimeException()
        whenever(
            cryptoService.findPublicKey(alias)
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.findPublicKey(alias)
        }
        assertSame(expected, actual.cause)
    }

    @Test
    @Timeout(5)
    fun `Should execute createWrappingKey`() {
        val circuitBreaker = createCircuitBreaker()
        val alias = UUID.randomUUID().toString()
        val argCaptor1 = argumentCaptor<String>()
        val argCaptor2 = argumentCaptor<Boolean>()
        circuitBreaker.createWrappingKey(alias, true)
        Mockito.verify(cryptoService, times(1)).createWrappingKey(
            argCaptor1.capture(),
            argCaptor2.capture()
        )
        assertEquals(alias, argCaptor1.firstValue)
        assertTrue(argCaptor2.firstValue)
    }

    @Test
    @Timeout(5)
    fun `Should throw same CryptoServiceException from wrapped service when executing createWrappingKey`() {
        val circuitBreaker = createCircuitBreaker()
        val alias = UUID.randomUUID().toString()
        val expected = CryptoServiceException("")
        whenever(
            cryptoService.createWrappingKey(alias, true)
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.createWrappingKey(alias, true)
        }
        assertSame(expected, actual)
    }

    @Test
    @Timeout(5)
    fun `Should throw same IllegalArgumentException from wrapped service when executing createWrappingKey`() {
        val circuitBreaker = createCircuitBreaker()
        val alias = UUID.randomUUID().toString()
        val expected = IllegalArgumentException()
        whenever(
            cryptoService.createWrappingKey(alias, true)
        ).thenThrow(expected)
        val actual = assertThrows<IllegalArgumentException> {
            circuitBreaker.createWrappingKey(alias, true)
        }
        assertSame(expected, actual)
    }

    @Test
    @Timeout(5)
    fun `Should throw exception wrapped in CryptoServiceException when executing createWrappingKey`() {
        val circuitBreaker = createCircuitBreaker()
        val alias = UUID.randomUUID().toString()
        val expected = RuntimeException()
        whenever(
            cryptoService.createWrappingKey(alias, true)
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.createWrappingKey(alias, true)
        }
        assertSame(expected, actual.cause)
    }

    @Test
    @Timeout(5)
    fun `Should execute generateKeyPair`() {
        val circuitBreaker = createCircuitBreaker()
        val alias = UUID.randomUUID().toString()
        val signatureScheme = RSA_SHA256_TEMPLATE.makeScheme(
            providerName = "Sun"
        )
        val expected = mock<PublicKey>()
        val context = emptyMap<String, String>()
        whenever(
            cryptoService.generateKeyPair(alias, signatureScheme, context)
        ).thenReturn(expected)
        assertSame(expected, circuitBreaker.generateKeyPair(alias, signatureScheme, context))
    }

    @Test
    @Timeout(5)
    fun `Should throw same CryptoServiceException from wrapped service when executing generateKeyPair`() {
        val circuitBreaker = createCircuitBreaker()
        val alias = UUID.randomUUID().toString()
        val signatureScheme = RSA_SHA256_TEMPLATE.makeScheme(
            providerName = "Sun"
        )
        val expected = CryptoServiceException("")
        val context = emptyMap<String, String>()
        whenever(
            cryptoService.generateKeyPair(alias, signatureScheme, context)
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.generateKeyPair(alias, signatureScheme, context)
        }
        assertSame(expected, actual)
    }

    @Test
    @Timeout(5)
    fun `Should throw exception wrapped in CryptoServiceException when executing generateKeyPair`() {
        val circuitBreaker = createCircuitBreaker()
        val alias = UUID.randomUUID().toString()
        val signatureScheme = RSA_SHA256_TEMPLATE.makeScheme(
            providerName = "Sun"
        )
        val expected = RuntimeException()
        val context = emptyMap<String, String>()
        whenever(
            cryptoService.generateKeyPair(alias, signatureScheme, context)
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.generateKeyPair(alias, signatureScheme, context)
        }
        assertSame(expected, actual.cause)
    }

    @Test
    @Timeout(5)
    fun `Should execute generateWrappedKeyPair`() {
        val circuitBreaker = createCircuitBreaker()
        val alias = UUID.randomUUID().toString()
        val signatureScheme = RSA_SHA256_TEMPLATE.makeScheme(
            providerName = "Sun"
        )
        val expected = WrappedKeyPair(
            mock(),
            UUID.randomUUID().toString().toByteArray(),
            1
        )
        val context = emptyMap<String, String>()
        whenever(
            cryptoService.generateWrappedKeyPair(alias, signatureScheme, context)
        ).thenReturn(expected)
        assertSame(expected, circuitBreaker.generateWrappedKeyPair(alias, signatureScheme, context))
    }

    @Test
    @Timeout(5)
    fun `Should throw same CryptoServiceException from wrapped service when executing generateWrappedKeyPair`() {
        val circuitBreaker = createCircuitBreaker()
        val alias = UUID.randomUUID().toString()
        val signatureScheme = RSA_SHA256_TEMPLATE.makeScheme(
            providerName = "Sun"
        )
        val expected = CryptoServiceException("")
        val context = emptyMap<String, String>()
        whenever(
            cryptoService.generateWrappedKeyPair(alias, signatureScheme, context)
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.generateWrappedKeyPair(alias, signatureScheme, context)
        }
        assertSame(expected, actual)
    }

    @Test
    @Timeout(5)
    fun `Should throw same IllegalArgumentException from wrapped service when executing generateWrappedKeyPair`() {
        val circuitBreaker = createCircuitBreaker()
        val alias = UUID.randomUUID().toString()
        val signatureScheme = RSA_SHA256_TEMPLATE.makeScheme(
            providerName = "Sun"
        )
        val expected = IllegalArgumentException()
        val context = emptyMap<String, String>()
        whenever(
            cryptoService.generateWrappedKeyPair(alias, signatureScheme, context)
        ).thenThrow(expected)
        val actual = assertThrows<IllegalArgumentException> {
            circuitBreaker.generateWrappedKeyPair(alias, signatureScheme, context)
        }
        assertSame(expected, actual)
    }

    @Test
    @Timeout(5)
    fun `Should throw exception wrapped in CryptoServiceException when executing generateWrappedKeyPair`() {
        val circuitBreaker = createCircuitBreaker()
        val alias = UUID.randomUUID().toString()
        val signatureScheme = RSA_SHA256_TEMPLATE.makeScheme(
            providerName = "Sun"
        )
        val expected = RuntimeException()
        val context = emptyMap<String, String>()
        whenever(
            cryptoService.generateWrappedKeyPair(alias, signatureScheme, context)
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.generateWrappedKeyPair(alias, signatureScheme, context)
        }
        assertSame(expected, actual.cause)
    }

    @Test
    @Timeout(5)
    fun `Should execute sign`() {
        val circuitBreaker = createCircuitBreaker()
        val alias = UUID.randomUUID().toString()
        val signatureScheme = RSA_SHA256_TEMPLATE.makeScheme(
            providerName = "Sun"
        )
        val data = UUID.randomUUID().toString().toByteArray()
        val expected = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        whenever(
            cryptoService.sign(alias, signatureScheme, data, context)
        ).thenReturn(expected)
        assertSame(expected, circuitBreaker.sign(alias, signatureScheme, data, context))
    }

    @Test
    @Timeout(5)
    fun `Should throw same CryptoServiceException from wrapped service when executing sign`() {
        val circuitBreaker = createCircuitBreaker()
        val alias = UUID.randomUUID().toString()
        val signatureScheme = RSA_SHA256_TEMPLATE.makeScheme(
            providerName = "Sun"
        )
        val data = UUID.randomUUID().toString().toByteArray()
        val expected = CryptoServiceException("")
        val context = emptyMap<String, String>()
        whenever(
            cryptoService.sign(alias, signatureScheme, data, context)
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.sign(alias, signatureScheme, data, context)
        }
        assertSame(expected, actual)
    }

    @Test
    @Timeout(5)
    fun `Should throw exception wrapped in CryptoServiceException when executing sign`() {
        val circuitBreaker = createCircuitBreaker()
        val alias = UUID.randomUUID().toString()
        val signatureScheme = RSA_SHA256_TEMPLATE.makeScheme(
            providerName = "Sun"
        )
        val data = UUID.randomUUID().toString().toByteArray()
        val expected = RuntimeException()
        val context = emptyMap<String, String>()
        whenever(
            cryptoService.sign(alias, signatureScheme, data, context)
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.sign(alias, signatureScheme, data, context)
        }
        assertSame(expected, actual.cause)
    }

    @Test
    @Timeout(5)
    @Suppress("MaxLineLength")
    fun `Should throw same CryptoServiceException from wrapped service when executing sign overload with wrapped key`() {
        val circuitBreaker = createCircuitBreaker()
        val wrappedKey = WrappedPrivateKey(
            keyMaterial = UUID.randomUUID().toString().toByteArray(),
            masterKeyAlias = UUID.randomUUID().toString(),
            signatureScheme = RSA_SHA256_TEMPLATE.makeScheme(
                providerName = "Sun"
            ),
            encodingVersion = 1
        )
        val data = UUID.randomUUID().toString().toByteArray()
        val expected = CryptoServiceException("")
        val context = emptyMap<String, String>()
        whenever(
            cryptoService.sign(wrappedKey, data, context)
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.sign(wrappedKey, data, context)
        }
        assertSame(expected, actual)
    }

    @Test
    @Timeout(5)
    @Suppress("MaxLineLength")
    fun `Should throw same IllegalArgumentException from wrapped service when executing sign overload with wrapped key`() {
        val circuitBreaker = createCircuitBreaker()
        val wrappedKey = WrappedPrivateKey(
            keyMaterial = UUID.randomUUID().toString().toByteArray(),
            masterKeyAlias = UUID.randomUUID().toString(),
            signatureScheme = RSA_SHA256_TEMPLATE.makeScheme(
                providerName = "Sun"
            ),
            encodingVersion = 1
        )
        val data = UUID.randomUUID().toString().toByteArray()
        val expected = IllegalArgumentException()
        val context = emptyMap<String, String>()
        whenever(
            cryptoService.sign(wrappedKey, data, context)
        ).thenThrow(expected)
        val actual = assertThrows<IllegalArgumentException> {
            circuitBreaker.sign(wrappedKey, data, context)
        }
        assertSame(expected, actual)
    }

    @Test
    @Timeout(5)
    @Suppress("MaxLineLength")
    fun `Should throw exception wrapped in CryptoServiceException when executing sign overload with wrapped key`() {
        val circuitBreaker = createCircuitBreaker()
        val wrappedKey = WrappedPrivateKey(
            keyMaterial = UUID.randomUUID().toString().toByteArray(),
            masterKeyAlias = UUID.randomUUID().toString(),
            signatureScheme = RSA_SHA256_TEMPLATE.makeScheme(
                providerName = "Sun"
            ),
            encodingVersion = 1
        )
        val data = UUID.randomUUID().toString().toByteArray()
        val expected = RuntimeException()
        val context = emptyMap<String, String>()
        whenever(
            cryptoService.sign(wrappedKey, data, context)
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.sign(wrappedKey, data, context)
        }
        assertSame(expected, actual.cause)
    }

    @Test
    @Timeout(5)
    fun `Should throw CryptoServiceTimeoutException on timeout`() {
        val circuitBreaker = createCircuitBreaker(0)
        val alias = UUID.randomUUID().toString()
        whenever(
            cryptoService.containsKey(alias)
        )
            .thenAnswer(AnswersWithDelay(1000, Returns(true)))
        assertThrows<CryptoServiceTimeoutException> {
            circuitBreaker.containsKey(alias)
        }
    }

    @Test
    @Timeout(5)
    fun `Should throw CryptoServiceTimeoutException on exceeding number of retries`() {
        val circuitBreaker = createCircuitBreaker(1)
        val alias = UUID.randomUUID().toString()
        whenever(
            cryptoService.containsKey(alias)
        )
            .thenAnswer(AnswersWithDelay(1000, Returns(true)))
            .thenAnswer(AnswersWithDelay(1000, Returns(true)))
        assertThrows<CryptoServiceTimeoutException> {
            circuitBreaker.containsKey(alias)
        }
    }

    @Test
    @Timeout(5)
    fun `Should eventually succeed after retry`() {
        val circuitBreaker = createCircuitBreaker(1)
        val alias = UUID.randomUUID().toString()
        whenever(
            cryptoService.containsKey(alias)
        )
            .thenAnswer(AnswersWithDelay(1000, Returns(true)))
            .thenReturn(true, false)
        assertTrue(circuitBreaker.containsKey(alias))
        assertFalse(circuitBreaker.containsKey(alias))
    }

    private fun createCircuitBreaker(retries: Long = 0): CryptoServiceDecorator {
        return CryptoServiceDecorator(
            cryptoService,
            Duration.ofMillis(500),
            retries = retries
        )
    }
}