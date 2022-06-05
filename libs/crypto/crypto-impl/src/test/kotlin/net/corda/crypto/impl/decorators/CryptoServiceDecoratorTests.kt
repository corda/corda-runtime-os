package net.corda.crypto.impl.decorators

import net.corda.crypto.core.CryptoConsts
import net.corda.v5.cipher.suite.CRYPTO_CATEGORY
import net.corda.v5.cipher.suite.CRYPTO_TENANT_ID
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.GeneratedKey
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.SigningSpec
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.cipher.suite.schemes.RSA_TEMPLATE
import net.corda.v5.crypto.exceptions.CryptoServiceException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.internal.stubbing.answers.AnswersWithDelay
import org.mockito.internal.stubbing.answers.Returns
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeoutException
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
    fun `Should close wrapped service`() {
        val decorator = createDecorator()
        decorator.close()
        Mockito.verify(cryptoService, times(1)).close()
    }

    @Test
    fun `Should execute requiresWrappingKey`() {
        val decorator = createDecorator()
        whenever(
            cryptoService.requiresWrappingKey()
        ).thenReturn(true, false)
        assertTrue(decorator.requiresWrappingKey())
        assertFalse(decorator.requiresWrappingKey())
    }

    @Test
    fun `Should throw same CryptoServiceException from wrapped service when executing requiresWrappingKey`() {
        val decorator = createDecorator()
        val expected = CryptoServiceException("")
        whenever(
            cryptoService.requiresWrappingKey()
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            decorator.requiresWrappingKey()
        }
        assertSame(expected, actual)
    }

    @Test
    fun `Should throw exception wrapped in CryptoServiceException when executing requiresWrappingKey`() {
        val decorator = createDecorator()
        val expected = RuntimeException()
        whenever(
            cryptoService.requiresWrappingKey()
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            decorator.requiresWrappingKey()
        }
        assertSame(expected, actual.cause)
    }

    @Test
    fun `Should execute supportedSchemes`() {
        val decorator = createDecorator()
        val expected = listOf<KeyScheme>()
        whenever(
            cryptoService.supportedSchemes()
        ).thenReturn(expected)
        assertSame(expected, decorator.supportedSchemes())
    }

    @Test
    fun `Should throw same CryptoServiceException from wrapped service when executing supportedSchemes`() {
        val decorator = createDecorator()
        val expected = CryptoServiceException("")
        whenever(
            cryptoService.supportedSchemes()
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            decorator.supportedSchemes()
        }
        assertSame(expected, actual)
    }

    @Test
    fun `Should throw exception wrapped in CryptoServiceException  when executing supportedSchemes`() {
        val decorator = createDecorator()
        val expected = RuntimeException()
        whenever(
            cryptoService.supportedSchemes()
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            decorator.supportedSchemes()
        }
        assertSame(expected, actual.cause)
    }

    @Test
    fun `Should execute createWrappingKey`() {
        val decorator = createDecorator()
        val alias = UUID.randomUUID().toString()
        val context = emptyMap<String, String>()
        val argCaptor1 = argumentCaptor<String>()
        val argCaptor2 = argumentCaptor<Boolean>()
        val argCaptor3 = argumentCaptor<Map<String, String>>()
        decorator.createWrappingKey(alias, true, context)
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
        val decorator = createDecorator()
        val alias = UUID.randomUUID().toString()
        val expected = CryptoServiceException("")
        whenever(
            cryptoService.createWrappingKey(alias, true, emptyMap())
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            decorator.createWrappingKey(alias, true, emptyMap())
        }
        assertSame(expected, actual)
    }

    @Test
    fun `Should throw same IllegalArgumentException from wrapped service when executing createWrappingKey`() {
        val decorator = createDecorator()
        val alias = UUID.randomUUID().toString()
        val expected = IllegalArgumentException()
        whenever(
            cryptoService.createWrappingKey(alias, true, emptyMap())
        ).thenThrow(expected)
        val actual = assertThrows<IllegalArgumentException> {
            decorator.createWrappingKey(alias, true, emptyMap())
        }
        assertSame(expected, actual)
    }

    @Test
    fun `Should throw exception wrapped in CryptoServiceException when executing createWrappingKey`() {
        val decorator = createDecorator()
        val alias = UUID.randomUUID().toString()
        val expected = RuntimeException()
        whenever(
            cryptoService.createWrappingKey(alias, true, emptyMap())
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            decorator.createWrappingKey(alias, true, emptyMap())
        }
        assertSame(expected, actual.cause)
    }

    @Test
    fun `Should execute generateKeyPair`() {
        val decorator = createDecorator()
        val tenantId = UUID.randomUUID().toString()
        val expectedAlias = UUID.randomUUID().toString()
        val expectedMasterKeyAlias = UUID.randomUUID().toString()
        val scheme = RSA_TEMPLATE.makeScheme(
            providerName = "Sun"
        )
        val aliasSecret = ByteArray(1)
        val expected = mock<GeneratedKey>()
        val context = mapOf(
            CRYPTO_TENANT_ID to tenantId,
            CRYPTO_CATEGORY to CryptoConsts.Categories.LEDGER
        )
        val spec = KeyGenerationSpec(
            alias = expectedAlias,
            masterKeyAlias = expectedMasterKeyAlias,
            keyScheme = scheme,
            secret = aliasSecret
        )
        whenever(
            cryptoService.generateKeyPair(
                spec,
                context)
        ).thenReturn(expected)
        assertSame(expected, decorator.generateKeyPair(spec, context))
        Mockito.verify(cryptoService, times(1)).generateKeyPair(
            argThat {
                keyScheme == scheme &&
                        alias == expectedAlias &&
                        masterKeyAlias == expectedMasterKeyAlias &&
                        secret.contentEquals(aliasSecret)
            },
            argThat {
                size == 2 &&
                this[CRYPTO_TENANT_ID] == tenantId &&
                this[CRYPTO_CATEGORY] == CryptoConsts.Categories.LEDGER
            }
        )
    }

    @Test
    fun `Should throw same CryptoServiceException from wrapped service when executing generateKeyPair`() {
        val decorator = createDecorator()
        val alias = UUID.randomUUID().toString()
        val masterKeyAlias = UUID.randomUUID().toString()
        val scheme = RSA_TEMPLATE.makeScheme(
            providerName = "Sun"
        )
        val expected = CryptoServiceException("")
        val context = emptyMap<String, String>()
        val spec = KeyGenerationSpec(
            alias = alias,
            masterKeyAlias = masterKeyAlias,
            keyScheme = scheme,
            secret = null
        )
        whenever(
            cryptoService.generateKeyPair(spec, context)
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            decorator.generateKeyPair(spec, context)
        }
        assertSame(expected, actual)
    }

    @Test
    fun `Should throw exception wrapped in CryptoServiceException when executing generateKeyPair`() {
        val decorator = createDecorator()
        val alias = UUID.randomUUID().toString()
        val masterKeyAlias = UUID.randomUUID().toString()
        val scheme = RSA_TEMPLATE.makeScheme(
            providerName = "Sun"
        )
        val expected = RuntimeException()
        val context = emptyMap<String, String>()
        val spec = KeyGenerationSpec(
            alias = alias,
            masterKeyAlias = masterKeyAlias,
            keyScheme = scheme,
            secret = null
        )
        whenever(
            cryptoService.generateKeyPair(spec, context)
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            decorator.generateKeyPair(spec, context)
        }
        assertSame(expected, actual.cause)
    }

    @Test
    fun `Should execute sign`() {
        val decorator = createDecorator()
        val data = UUID.randomUUID().toString().toByteArray()
        val expected = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val spec = mock<SigningSpec>()
        whenever(
            cryptoService.sign(spec, data, context)
        ).thenReturn(expected)
        assertSame(expected, decorator.sign(spec, data, context))
    }

    @Test
    fun `Should throw same CryptoServiceException from wrapped service when executing sign`() {
        val decorator = createDecorator()
        val data = UUID.randomUUID().toString().toByteArray()
        val expected = CryptoServiceException("")
        val context = emptyMap<String, String>()
        val spec = mock<SigningSpec>()
        whenever(
            cryptoService.sign(spec, data, context)
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            decorator.sign(spec, data, context)
        }
        assertSame(expected, actual)
    }

    @Test
    fun `Should throw exception wrapped in CryptoServiceException when executing sign`() {
        val decorator = createDecorator()
        val data = UUID.randomUUID().toString().toByteArray()
        val expected = RuntimeException()
        val context = emptyMap<String, String>()
        val spec = mock<SigningSpec>()
        whenever(
            cryptoService.sign(spec, data, context)
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            decorator.sign(spec, data, context)
        }
        assertSame(expected, actual.cause)
    }

    @Test
    fun `Should throw CryptoServiceTimeoutException on timeout`() {
        val decorator = createDecorator(0)
        val data = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val spec = mock<SigningSpec>()
        val expected = UUID.randomUUID().toString().toByteArray()
        whenever(
            cryptoService.sign(spec, data, context)
        ).thenAnswer(AnswersWithDelay(500, Returns(expected)))
        val e = assertThrows<CryptoServiceException> {
            decorator.sign(spec, data, context)
        }
        assertThat(e.cause).isInstanceOf(TimeoutException::class.java)
    }

    @Test
    fun `Should throw CryptoServiceTimeoutException on exceeding number of retries`() {
        val decorator = createDecorator(1)
        val data = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val spec = mock<SigningSpec>()
        val expected = UUID.randomUUID().toString().toByteArray()
        whenever(
            cryptoService.sign(spec, data, context)
        )
        .thenAnswer(AnswersWithDelay(500, Returns(expected)))
        .thenAnswer(AnswersWithDelay(500, Returns(expected)))
        val e = assertThrows<CryptoServiceException> {
            decorator.sign(spec, data, context)
        }
        assertThat(e.cause).isInstanceOf(TimeoutException::class.java)
    }

    @Test
    fun `Should eventually succeed after retry`() {
        val decorator = createDecorator(2)
        val data = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val spec = mock<SigningSpec>()
        val expected1 = UUID.randomUUID().toString().toByteArray()
        val expected2 = UUID.randomUUID().toString().toByteArray()
        whenever(
            cryptoService.sign(spec, data, context)
        )
        .thenAnswer(AnswersWithDelay(500, Returns(expected1)))
        .thenReturn(expected2, expected1)
        assertSame(expected2, decorator.sign(spec, data, context))
        assertSame(expected1, decorator.sign(spec, data, context))
    }

    private fun createDecorator(retries: Int = 0): CryptoServiceDecorator {
        return CryptoServiceDecorator(
            cryptoService,
            Duration.ofMillis(250),
            retries = retries
        )
    }
}