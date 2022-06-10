package net.corda.crypto.impl.decorators

import net.corda.crypto.core.CryptoConsts
import net.corda.v5.cipher.suite.CRYPTO_CATEGORY
import net.corda.v5.cipher.suite.CRYPTO_TENANT_ID
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceExtensions
import net.corda.v5.cipher.suite.GeneratedKey
import net.corda.v5.cipher.suite.KeyGenerationSpec
import net.corda.v5.cipher.suite.SigningSpec
import net.corda.v5.cipher.suite.schemes.KeyScheme
import net.corda.v5.cipher.suite.schemes.RSA_TEMPLATE
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CSLExponentialThrottlingException
import net.corda.v5.crypto.exceptions.CryptoServiceException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
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
    companion object {
        @JvmStatic
        fun mostCommonUnrecoverableExceptions(): List<Throwable> = listOf(
            IllegalStateException(),
            IllegalArgumentException(),
            NullPointerException(),
            IndexOutOfBoundsException(),
            NoSuchElementException(),
            RuntimeException(),
            ClassCastException(),
            NotImplementedError(),
            UnsupportedOperationException()
        )
    }

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
    fun `Should execute extensions method`() {
        val decorator = createDecorator()
        whenever(
            cryptoService.extensions
        ).thenReturn(
            listOf(CryptoServiceExtensions.REQUIRE_WRAPPING_KEY),
            listOf(CryptoServiceExtensions.DELETE_KEYS)
        )
        assertEquals(CryptoServiceExtensions.REQUIRE_WRAPPING_KEY, decorator.extensions[0])
        assertEquals(CryptoServiceExtensions.DELETE_KEYS, decorator.extensions[0])
    }

    @Test
    fun `Should throw non recoverable CryptoServiceException when executing extensions method`() {
        val decorator = createDecorator()
        val expected = CryptoServiceException("error", isRecoverable = false)
        whenever(
            cryptoService.extensions
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            decorator.extensions
        }
        assertFalse(actual.isRecoverable)
    }

    @ParameterizedTest
    @MethodSource("mostCommonUnrecoverableExceptions")
    fun `Should throw common exceptions wrapped in CryptoServiceException when executing extensions method`(
        e: Throwable
    ) {
        val decorator = createDecorator()
        whenever(
            cryptoService.extensions
        ).thenThrow(e)
        val actual = assertThrows<CryptoServiceException> {
            decorator.extensions
        }
        assertSame(e, actual.cause)
    }

    @Test
    fun `Should execute supportedSchemes`() {
        val decorator = createDecorator()
        val expected = mapOf<KeyScheme, List<SignatureSpec>>()
        whenever(
            cryptoService.supportedSchemes
        ).thenReturn(expected)
        assertSame(expected, decorator.supportedSchemes)
    }

    @Test
    fun `Should throw non recoverable CryptoServiceException when executing supportedSchemes`() {
        val decorator = createDecorator()
        val expected = CryptoServiceException("error", isRecoverable = false)
        whenever(
            cryptoService.supportedSchemes
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            decorator.supportedSchemes
        }
        assertFalse(actual.isRecoverable)
    }

    @ParameterizedTest
    @MethodSource("mostCommonUnrecoverableExceptions")
    fun `Should throw common exceptions wrapped in CryptoServiceException when executing supportedSchemes`(
        e: Throwable
    ) {
        val decorator = createDecorator()
        whenever(
            cryptoService.supportedSchemes
        ).thenThrow(e)
        val actual = assertThrows<CryptoServiceException> {
            decorator.supportedSchemes
        }
        assertSame(e, actual.cause)
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
    fun `Should throw non recoverable CryptoServiceException when executing createWrappingKey`() {
        val decorator = createDecorator()
        val alias = UUID.randomUUID().toString()
        val expected = CryptoServiceException("error", isRecoverable = false)
        whenever(
            cryptoService.createWrappingKey(alias, true, emptyMap())
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            decorator.createWrappingKey(alias, true, emptyMap())
        }
        assertFalse(actual.isRecoverable)
    }

    @ParameterizedTest
    @MethodSource("mostCommonUnrecoverableExceptions")
    fun `Should throw common exceptions wrapped in CryptoServiceException when executing createWrappingKey`(
        e: Throwable
    ) {
        val decorator = createDecorator()
        val alias = UUID.randomUUID().toString()
        whenever(
            cryptoService.createWrappingKey(alias, true, emptyMap())
        ).thenThrow(e)
        val actual = assertThrows<CryptoServiceException> {
            decorator.createWrappingKey(alias, true, emptyMap())
        }
        assertSame(e, actual.cause)
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
    fun `Should throw non recoverable CryptoServiceException when executing generateKeyPair`() {
        val decorator = createDecorator()
        val alias = UUID.randomUUID().toString()
        val masterKeyAlias = UUID.randomUUID().toString()
        val scheme = RSA_TEMPLATE.makeScheme(
            providerName = "Sun"
        )
        val expected = CryptoServiceException("error", isRecoverable = false)
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
        assertFalse(actual.isRecoverable)
    }

    @ParameterizedTest
    @MethodSource("mostCommonUnrecoverableExceptions")
    fun `Should throw common exceptions wrapped in CryptoServiceException when executing generateKeyPair`(
        e: Throwable
    ) {
        val decorator = createDecorator()
        val alias = UUID.randomUUID().toString()
        val masterKeyAlias = UUID.randomUUID().toString()
        val scheme = RSA_TEMPLATE.makeScheme(
            providerName = "Sun"
        )
        val context = emptyMap<String, String>()
        val spec = KeyGenerationSpec(
            alias = alias,
            masterKeyAlias = masterKeyAlias,
            keyScheme = scheme,
            secret = null
        )
        whenever(
            cryptoService.generateKeyPair(spec, context)
        ).thenThrow(e)
        val actual = assertThrows<CryptoServiceException> {
            decorator.generateKeyPair(spec, context)
        }
        assertSame(e, actual.cause)
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
    fun `Should throw non recoverable CryptoServiceException when executing sign`() {
        val decorator = createDecorator()
        val data = UUID.randomUUID().toString().toByteArray()
        val expected = CryptoServiceException("error", isRecoverable = false)
        val context = emptyMap<String, String>()
        val spec = mock<SigningSpec>()
        whenever(
            cryptoService.sign(spec, data, context)
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            decorator.sign(spec, data, context)
        }
        assertFalse(actual.isRecoverable)
    }

    @ParameterizedTest
    @MethodSource("mostCommonUnrecoverableExceptions")
    fun `Should throw common exceptions wrapped in CryptoServiceException when executing sign`(e: Throwable) {
        val decorator = createDecorator()
        val data = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val spec = mock<SigningSpec>()
        whenever(
            cryptoService.sign(spec, data, context)
        ).thenThrow(e)
        val actual = assertThrows<CryptoServiceException> {
            decorator.sign(spec, data, context)
        }
        assertSame(e, actual.cause)
    }

    @Test
    fun `Should execute delete`() {
        val decorator = createDecorator()
        val alias = UUID.randomUUID().toString()
        val context = emptyMap<String, String>()
        decorator.delete(alias, context)
        Mockito.verify(cryptoService, times(1)).delete(alias, context)
    }

    @Test
    fun `Should throw non recoverable CryptoServiceException when executing delete`() {
        val decorator = createDecorator()
        val expected = CryptoServiceException("error", isRecoverable = false)
        val alias = UUID.randomUUID().toString()
        val context = emptyMap<String, String>()
        whenever(
            cryptoService.delete(alias, context)
        ).thenThrow(expected)
        val actual = assertThrows<CryptoServiceException> {
            decorator.delete(alias, context)
        }
        assertFalse(actual.isRecoverable)
    }

    @ParameterizedTest
    @MethodSource("mostCommonUnrecoverableExceptions")
    fun `Should throw common exceptions wrapped in CryptoServiceException when executing delete`(e: Throwable) {
        val decorator = createDecorator()
        val alias = UUID.randomUUID().toString()
        val context = emptyMap<String, String>()
        whenever(
            cryptoService.delete(alias, context)
        ).thenThrow(e)
        val actual = assertThrows<CryptoServiceException> {
            decorator.delete(alias, context)
        }
        assertSame(e, actual.cause)
    }

    @Test
    fun `Should throw TimeoutException wrapped into CryptoServiceException on timeout`() {
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
    fun `Should throw CryptoServiceException on exceeding number of retries`() {
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

    @Test
    fun `Should eventually succeed after throttling`() {
        val decorator = createDecorator(2)
        val data = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val spec = mock<SigningSpec>()
        val expected1 = UUID.randomUUID().toString().toByteArray()
        val expected2 = UUID.randomUUID().toString().toByteArray()
        whenever(
            cryptoService.sign(spec, data, context)
        )
            .thenThrow(CSLExponentialThrottlingException(
                message = "error",
                initialBackoff =100,
                backoffMultiplier = 2,
                maxAttempts = 3,
                cause = IllegalStateException()
            ))
            .thenReturn(expected2, expected1)
        assertSame(expected2, decorator.sign(spec, data, context))
        assertSame(expected1, decorator.sign(spec, data, context))
    }

    @Test
    fun `Should fail if throttling didn't go away`() {
        val decorator = createDecorator(2)
        val data = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val spec = mock<SigningSpec>()
        val e = CSLExponentialThrottlingException(
            message = "error",
            initialBackoff =100,
            backoffMultiplier = 2,
            maxAttempts = 3,
            cause = IllegalStateException()
        )
        whenever(
            cryptoService.sign(spec, data, context)
        )
            .thenThrow(e, e, e)
        assertThrows<CryptoServiceException> {
            decorator.sign(spec, data, context)
        }
    }

    private fun createDecorator(maxAttempts: Int = 1): CryptoServiceDecorator {
        return CryptoServiceDecorator(
            cryptoService,
            Duration.ofMillis(250),
            maxAttempts = maxAttempts
        )
    }
}