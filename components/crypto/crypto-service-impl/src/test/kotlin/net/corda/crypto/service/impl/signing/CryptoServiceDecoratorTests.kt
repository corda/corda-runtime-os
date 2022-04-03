package net.corda.crypto.service.impl.signing

import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.GeneratedKey
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.SigningSpec
import net.corda.v5.cipher.suite.schemes.RSA_SHA256_TEMPLATE
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.exceptions.CryptoServiceException
import net.corda.v5.crypto.exceptions.CryptoServiceTimeoutException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.internal.stubbing.answers.AnswersWithDelay
import org.mockito.internal.stubbing.answers.Returns
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
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
    fun `Should close wrapped service`() {
        val circuitBreaker = createCircuitBreaker()
        circuitBreaker.close()
        Mockito.verify(cryptoService, times(1)).close()
    }

    @Test
    fun `Should execute requiresWrappingKey`() {
        val circuitBreaker = createCircuitBreaker()
        whenever(
            cryptoService.requiresWrappingKey()
        ).thenReturn(true, false)
        assertTrue(circuitBreaker.requiresWrappingKey())
        assertFalse(circuitBreaker.requiresWrappingKey())
    }

    @Test
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
    fun `Should execute supportedSchemes`() {
        val circuitBreaker = createCircuitBreaker()
        val expected = arrayOf<SignatureScheme>()
        whenever(
            cryptoService.supportedSchemes()
        ).thenReturn(expected)
        assertSame(expected, circuitBreaker.supportedSchemes())
    }

    @Test
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
    fun `Should execute createWrappingKey`() {
        val circuitBreaker = createCircuitBreaker()
        val alias = UUID.randomUUID().toString()
        val context = emptyMap<String, String>()
        val argCaptor1 = argumentCaptor<String>()
        val argCaptor2 = argumentCaptor<Boolean>()
        val argCaptor3 = argumentCaptor<Map<String, String>>()
        circuitBreaker.createWrappingKey(alias, true, context)
        Mockito.verify(cryptoService, times(1)).createWrappingKey(
            argCaptor1.capture(),
            argCaptor2.capture(),
            argCaptor3.capture()
        )
        assertEquals(alias, argCaptor1.firstValue)
        assertTrue(argCaptor2.firstValue)
        assertSame(context, argCaptor3.firstValue)
    }

    @Test
    fun `Should throw same CryptoServiceException from wrapped service when executing createWrappingKey`() {
        val circuitBreaker = createCircuitBreaker()
        val alias = UUID.randomUUID().toString()
        val expected = CryptoServiceException("")
        whenever(
            cryptoService.createWrappingKey(alias, true, emptyMap())
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.createWrappingKey(alias, true, emptyMap())
        }
        assertSame(expected, actual)
    }

    @Test
    fun `Should throw same IllegalArgumentException from wrapped service when executing createWrappingKey`() {
        val circuitBreaker = createCircuitBreaker()
        val alias = UUID.randomUUID().toString()
        val expected = IllegalArgumentException()
        whenever(
            cryptoService.createWrappingKey(alias, true, emptyMap())
        ).thenThrow(expected)
        val actual = assertThrows<IllegalArgumentException> {
            circuitBreaker.createWrappingKey(alias, true, emptyMap())
        }
        assertSame(expected, actual)
    }

    @Test
    fun `Should throw exception wrapped in CryptoServiceException when executing createWrappingKey`() {
        val circuitBreaker = createCircuitBreaker()
        val alias = UUID.randomUUID().toString()
        val expected = RuntimeException()
        whenever(
            cryptoService.createWrappingKey(alias, true, emptyMap())
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.createWrappingKey(alias, true, emptyMap())
        }
        assertSame(expected, actual.cause)
    }

    @Test
    fun `Should execute generateKeyPair`() {
        val circuitBreaker = createCircuitBreaker()
        val tenantId = UUID.randomUUID().toString()
        val alias = UUID.randomUUID().toString()
        val masterKeyAlias = UUID.randomUUID().toString()
        val signatureScheme = RSA_SHA256_TEMPLATE.makeScheme(
            providerName = "Sun"
        )
        val expected = mock<GeneratedKey>()
        val context = emptyMap<String, String>()
        val spec = KeyGenerationSpec(
            tenantId = tenantId,
            alias = alias,
            masterKeyAlias = masterKeyAlias,
            signatureScheme = signatureScheme,
            secret = null
        )
        whenever(
            cryptoService.generateKeyPair(
                spec,
                context)
        ).thenReturn(expected)
        assertSame(expected, circuitBreaker.generateKeyPair(spec, context))
    }

    @Test
    fun `Should throw same CryptoServiceException from wrapped service when executing generateKeyPair`() {
        val circuitBreaker = createCircuitBreaker()
        val tenantId = UUID.randomUUID().toString()
        val alias = UUID.randomUUID().toString()
        val masterKeyAlias = UUID.randomUUID().toString()
        val signatureScheme = RSA_SHA256_TEMPLATE.makeScheme(
            providerName = "Sun"
        )
        val expected = CryptoServiceException("")
        val context = emptyMap<String, String>()
        val spec = KeyGenerationSpec(
            tenantId = tenantId,
            alias = alias,
            masterKeyAlias = masterKeyAlias,
            signatureScheme = signatureScheme,
            secret = null
        )
        whenever(
            cryptoService.generateKeyPair(spec, context)
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.generateKeyPair(spec, context)
        }
        assertSame(expected, actual)
    }

    @Test
    fun `Should throw exception wrapped in CryptoServiceException when executing generateKeyPair`() {
        val circuitBreaker = createCircuitBreaker()
        val tenantId = UUID.randomUUID().toString()
        val alias = UUID.randomUUID().toString()
        val masterKeyAlias = UUID.randomUUID().toString()
        val signatureScheme = RSA_SHA256_TEMPLATE.makeScheme(
            providerName = "Sun"
        )
        val expected = RuntimeException()
        val context = emptyMap<String, String>()
        val spec = KeyGenerationSpec(
            tenantId = tenantId,
            alias = alias,
            masterKeyAlias = masterKeyAlias,
            signatureScheme = signatureScheme,
            secret = null
        )
        whenever(
            cryptoService.generateKeyPair(spec, context)
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.generateKeyPair(spec, context)
        }
        assertSame(expected, actual.cause)
    }

    @Test
    fun `Should execute sign`() {
        val circuitBreaker = createCircuitBreaker()
        val data = UUID.randomUUID().toString().toByteArray()
        val expected = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val spec = mock<SigningSpec>()
        whenever(
            cryptoService.sign(spec, data, context)
        ).thenReturn(expected)
        assertSame(expected, circuitBreaker.sign(spec, data, context))
    }

    @Test
    fun `Should throw same CryptoServiceException from wrapped service when executing sign`() {
        val circuitBreaker = createCircuitBreaker()
        val data = UUID.randomUUID().toString().toByteArray()
        val expected = CryptoServiceException("")
        val context = emptyMap<String, String>()
        val spec = mock<SigningSpec>()
        whenever(
            cryptoService.sign(spec, data, context)
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.sign(spec, data, context)
        }
        assertSame(expected, actual)
    }

    @Test
    fun `Should throw exception wrapped in CryptoServiceException when executing sign`() {
        val circuitBreaker = createCircuitBreaker()
        val data = UUID.randomUUID().toString().toByteArray()
        val expected = RuntimeException()
        val context = emptyMap<String, String>()
        val spec = mock<SigningSpec>()
        whenever(
            cryptoService.sign(spec, data, context)
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            circuitBreaker.sign(spec, data, context)
        }
        assertSame(expected, actual.cause)
    }

    @Test
    fun `Should throw CryptoServiceTimeoutException on timeout`() {
        val circuitBreaker = createCircuitBreaker(0)
        val data = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val spec = mock<SigningSpec>()
        val expected = UUID.randomUUID().toString().toByteArray()
        whenever(
            cryptoService.sign(spec, data, context)
        ).thenAnswer(AnswersWithDelay(1000, Returns(expected)))
        assertThrows<CryptoServiceTimeoutException> {
            circuitBreaker.sign(spec, data, context)
        }
    }

    @Test
    fun `Should throw CryptoServiceTimeoutException on exceeding number of retries`() {
        val circuitBreaker = createCircuitBreaker(1)
        val data = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val spec = mock<SigningSpec>()
        val expected = UUID.randomUUID().toString().toByteArray()
        whenever(
            cryptoService.sign(spec, data, context)
        )
        .thenAnswer(AnswersWithDelay(1000, Returns(expected)))
        .thenAnswer(AnswersWithDelay(1000, Returns(expected)))
        assertThrows<CryptoServiceTimeoutException> {
            circuitBreaker.sign(spec, data, context)
        }
    }

    @Test
    fun `Should eventually succeed after retry`() {
        val circuitBreaker = createCircuitBreaker(1)
        val data = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val spec = mock<SigningSpec>()
        val expected1 = UUID.randomUUID().toString().toByteArray()
        val expected2 = UUID.randomUUID().toString().toByteArray()
        whenever(
            cryptoService.sign(spec, data, context)
        )
        .thenAnswer(AnswersWithDelay(1000, Returns(expected1)))
        .thenReturn(expected2, expected1)
        assertSame(expected2, circuitBreaker.sign(spec, data, context))
        assertSame(expected1, circuitBreaker.sign(spec, data, context))
    }

    private fun createCircuitBreaker(retries: Int = 0): CryptoServiceDecorator {
        return CryptoServiceDecorator(
            cryptoService,
            Duration.ofMillis(500),
            retries = retries
        )
    }
}