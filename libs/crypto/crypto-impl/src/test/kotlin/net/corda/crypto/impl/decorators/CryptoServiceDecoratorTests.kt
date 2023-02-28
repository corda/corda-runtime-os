package net.corda.crypto.impl.decorators

import net.corda.crypto.cipher.suite.CRYPTO_CATEGORY
import net.corda.crypto.cipher.suite.CRYPTO_TENANT_ID
import net.corda.crypto.cipher.suite.CryptoService
import net.corda.crypto.cipher.suite.CryptoServiceExtensions
import net.corda.crypto.cipher.suite.CryptoThrottlingException
import net.corda.crypto.cipher.suite.GeneratedKey
import net.corda.crypto.cipher.suite.KeyGenerationSpec
import net.corda.crypto.cipher.suite.SharedSecretSpec
import net.corda.crypto.cipher.suite.SigningSpec
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.crypto.cipher.suite.schemes.RSA_TEMPLATE
import net.corda.crypto.core.CryptoConsts
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoException
import net.corda.v5.crypto.exceptions.CryptoRetryException
import net.corda.v5.crypto.exceptions.CryptoSignatureException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
import java.security.SignatureException
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CryptoServiceDecoratorTests {
    companion object {
        @JvmStatic
        fun recoverableExceptions(): List<Throwable> = listOf(
            CryptoException("error", true),
            TimeoutException(),
            javax.persistence.LockTimeoutException(),
            javax.persistence.QueryTimeoutException(),
            javax.persistence.OptimisticLockException(),
            javax.persistence.PessimisticLockException(),
            java.sql.SQLTransientException(),
            java.sql.SQLTimeoutException(),
            org.hibernate.exception.LockAcquisitionException("error", java.sql.SQLException()),
            org.hibernate.exception.LockTimeoutException("error", java.sql.SQLException()),
            RuntimeException("error", TimeoutException()),
            javax.persistence.PersistenceException("error", javax.persistence.LockTimeoutException()),
            javax.persistence.PersistenceException("error", javax.persistence.QueryTimeoutException()),
            javax.persistence.PersistenceException("error", javax.persistence.OptimisticLockException()),
            javax.persistence.PersistenceException("error", javax.persistence.PessimisticLockException()),
            javax.persistence.PersistenceException("error", java.sql.SQLTransientException()),
            javax.persistence.PersistenceException("error", java.sql.SQLTimeoutException()),
            javax.persistence.PersistenceException("error", org.hibernate.exception.LockAcquisitionException(
                "error", java.sql.SQLException()
            )
            ),
            javax.persistence.PersistenceException("error", org.hibernate.exception.LockTimeoutException(
                "error", java.sql.SQLException()
            ))
        )

        @JvmStatic
        fun mostCommonUnrecoverableExceptions(): List<Throwable> = listOf(
            IllegalStateException(),
            IllegalArgumentException(),
            NullPointerException(),
            IndexOutOfBoundsException(),
            NoSuchElementException(),
            RuntimeException(),
            ClassCastException(),
            UnsupportedOperationException(),
            CryptoException("error"),
            CryptoException(
                "error",
                CryptoException("error", true)
            ),
            CryptoException(
                "error",
                TimeoutException()
            ),
            CryptoRetryException("error", TimeoutException()),
            CryptoSignatureException("error", SignatureException()),
            javax.persistence.PersistenceException()
        )

        @JvmStatic
        fun mostCommonCheckedExceptions(): List<Throwable> = listOf(
            NotImplementedError(),
            Error(),
            SignatureException()
        )
    }

    private interface CryptoServiceStub : CryptoService, AutoCloseable

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

    @ParameterizedTest
    @MethodSource("mostCommonUnrecoverableExceptions")
    fun `Should throw same non recoverable exception when executing extensions`(expected: Throwable) {
        val decorator = createDecorator()
        whenever(
            cryptoService.extensions
        ).thenAnswer { throw expected }
        val actual = assertThrows(expected::class.java) {
            decorator.extensions
        }
        assertSame(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("mostCommonCheckedExceptions")
    fun `Should throw checked exception wrapped in CryptoException when executing extensions`(
        expected: Throwable
    ) {
        val decorator = createDecorator()
        whenever(
            cryptoService.extensions
        ).thenAnswer { throw expected }
        val actual = assertThrows(CryptoException::class.java) {
            decorator.extensions
        }
        assertFalse(actual.isRecoverable)
        assertSame(expected, actual.cause)
    }

    @ParameterizedTest
    @MethodSource("recoverableExceptions")
    fun `Should throw original recoverable exceptions wrapped in CryptoException when executing extensions`(
        expected: Throwable
    ) {
        val decorator = createDecorator()
        whenever(
            cryptoService.extensions
        ).thenAnswer { throw expected }
        val actual = assertThrows(CryptoException::class.java) {
            decorator.extensions
        }
        assertFalse(actual.isRecoverable)
        assertSame(expected, actual.cause)
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

    @ParameterizedTest
    @MethodSource("mostCommonUnrecoverableExceptions")
    fun `Should throw same non recoverable exception when executing supportedSchemes`(expected: Throwable) {
        val decorator = createDecorator()
        whenever(
            cryptoService.supportedSchemes
        ).thenAnswer { throw expected }
        val actual = assertThrows(expected::class.java) {
            decorator.supportedSchemes
        }
        assertSame(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("mostCommonCheckedExceptions")
    fun `Should throw checked exception wrapped in CryptoException when executing supportedSchemes`(
        expected: Throwable
    ) {
        val decorator = createDecorator()
        whenever(
            cryptoService.supportedSchemes
        ).thenAnswer { throw expected }
        val actual = assertThrows(CryptoException::class.java) {
            decorator.supportedSchemes
        }
        assertFalse(actual.isRecoverable)
        assertSame(expected, actual.cause)
    }

    @ParameterizedTest
    @MethodSource("recoverableExceptions")
    fun `Should throw original recoverable exceptions wrapped in CryptoException when executing supportedSchemes`(
        e: Throwable
    ) {
        val decorator = createDecorator()
        whenever(
            cryptoService.supportedSchemes
        ).thenAnswer { throw e }
        val actual = assertThrows(CryptoException::class.java) {
            decorator.supportedSchemes
        }
        assertFalse(actual.isRecoverable)
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

    @ParameterizedTest
    @MethodSource("mostCommonUnrecoverableExceptions")
    fun `Should throw same non recoverable exception when executing createWrappingKey`(expected: Throwable) {
        val decorator = createDecorator()
        val alias = UUID.randomUUID().toString()
        whenever(
            cryptoService.createWrappingKey(alias, true, emptyMap())
        ).thenAnswer { throw expected }
        val actual = assertThrows(expected::class.java) {
            decorator.createWrappingKey(alias, true, emptyMap())
        }
        assertSame(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("mostCommonCheckedExceptions")
    fun `Should throw checked exception wrapped in CryptoException when executing createWrappingKey`(
        expected: Throwable
    ) {
        val decorator = createDecorator()
        val alias = UUID.randomUUID().toString()
        whenever(
            cryptoService.createWrappingKey(alias, true, emptyMap())
        ).thenAnswer { throw expected }
        val actual = assertThrows(CryptoException::class.java) {
            decorator.createWrappingKey(alias, true, emptyMap())
        }
        assertFalse(actual.isRecoverable)
        assertSame(expected, actual.cause)
    }

    @ParameterizedTest
    @MethodSource("recoverableExceptions")
    fun `Should throw original recoverable exceptions wrapped in CryptoException when executing createWrappingKey`(
        e: Throwable
    ) {
        val decorator = createDecorator()
        val alias = UUID.randomUUID().toString()
        whenever(
            cryptoService.createWrappingKey(alias, true, emptyMap())
        ).thenAnswer { throw e }
        val actual = assertThrows(CryptoException::class.java) {
            decorator.createWrappingKey(alias, true, emptyMap())
        }
        assertFalse(actual.isRecoverable)
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
        val expected = mock<GeneratedKey>()
        val context = mapOf(
            CRYPTO_TENANT_ID to tenantId,
            CRYPTO_CATEGORY to CryptoConsts.Categories.LEDGER
        )
        val spec = KeyGenerationSpec(
            alias = expectedAlias,
            masterKeyAlias = expectedMasterKeyAlias,
            keyScheme = scheme
        )
        whenever(
            cryptoService.generateKeyPair(
                spec,
                context
            )
        ).thenReturn(expected)
        assertSame(expected, decorator.generateKeyPair(spec, context))
        Mockito.verify(cryptoService, times(1)).generateKeyPair(
            argThat {
                keyScheme == scheme &&
                        alias == expectedAlias &&
                        masterKeyAlias == expectedMasterKeyAlias
            },
            argThat {
                size == 2 &&
                        this[CRYPTO_TENANT_ID] == tenantId &&
                        this[CRYPTO_CATEGORY] == CryptoConsts.Categories.LEDGER
            }
        )
    }

    @ParameterizedTest
    @MethodSource("mostCommonUnrecoverableExceptions")
    fun `Should throw same non recoverable exception when executing generateKeyPair`(expected: Throwable) {
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
            keyScheme = scheme
        )
        whenever(
            cryptoService.generateKeyPair(spec, context)
        ).thenAnswer { throw expected }
        val actual = assertThrows(expected::class.java) {
            decorator.generateKeyPair(spec, context)
        }
        assertSame(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("mostCommonCheckedExceptions")
    fun `Should throw checked exception wrapped in CryptoException when executing generateKeyPair`(
        expected: Throwable
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
            keyScheme = scheme
        )
        whenever(
            cryptoService.generateKeyPair(spec, context)
        ).thenAnswer { throw expected }
        val actual = assertThrows(CryptoException::class.java) {
            decorator.generateKeyPair(spec, context)
        }
        assertFalse(actual.isRecoverable)
        assertSame(expected, actual.cause)
    }

    @ParameterizedTest
    @MethodSource("recoverableExceptions")
    fun `Should throw original recoverable exceptions wrapped in CryptoException when executing generateKeyPair`(
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
            keyScheme = scheme
        )
        whenever(
            cryptoService.generateKeyPair(spec, context)
        ).thenAnswer { throw e }
        val actual = assertThrows(CryptoException::class.java) {
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

    @ParameterizedTest
    @MethodSource("mostCommonUnrecoverableExceptions")
    fun `Should throw same non recoverable exception when executing sign`(
        expected: Throwable
    ) {
        val decorator = createDecorator()
        val data = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val spec = mock<SigningSpec>()
        whenever(
            cryptoService.sign(spec, data, context)
        ).thenAnswer { throw expected }
        val actual = assertThrows(expected::class.java) {
            decorator.sign(spec, data, context)
        }
        assertSame(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("mostCommonCheckedExceptions")
    fun `Should throw checked exception wrapped in CryptoException when executing sign`(
        expected: Throwable
    ) {
        val decorator = createDecorator()
        val data = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val spec = mock<SigningSpec>()
        whenever(
            cryptoService.sign(spec, data, context)
        ).thenAnswer { throw expected }
        val actual = assertThrows(CryptoException::class.java) {
            decorator.sign(spec, data, context)
        }
        assertFalse(actual.isRecoverable)
        assertSame(expected, actual.cause)
    }

    @ParameterizedTest
    @MethodSource("recoverableExceptions")
    fun `Should throw original recoverable exceptions wrapped in CryptoException when executing sign`(e: Throwable) {
        val decorator = createDecorator()
        val data = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val spec = mock<SigningSpec>()
        whenever(
            cryptoService.sign(spec, data, context)
        ).thenAnswer { throw e }
        val actual = assertThrows(CryptoException::class.java) {
            decorator.sign(spec, data, context)
        }
        assertFalse(actual.isRecoverable)
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

    @ParameterizedTest
    @MethodSource("mostCommonUnrecoverableExceptions")
    fun `Should throw same non recoverable exception when executing delete`(expected: Throwable) {
        val decorator = createDecorator()
        val alias = UUID.randomUUID().toString()
        val context = emptyMap<String, String>()
        whenever(
            cryptoService.delete(alias, context)
        ).thenAnswer { throw expected }
        val actual = assertThrows(expected::class.java) {
            decorator.delete(alias, context)
        }
        assertSame(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("mostCommonCheckedExceptions")
    fun `Should throw checked exception wrapped in CryptoException when executing delete`(
        expected: Throwable
    ) {
        val decorator = createDecorator()
        val alias = UUID.randomUUID().toString()
        val context = emptyMap<String, String>()
        whenever(
            cryptoService.delete(alias, context)
        ).thenAnswer { throw expected }
        val actual = assertThrows(CryptoException::class.java) {
            decorator.delete(alias, context)
        }
        assertFalse(actual.isRecoverable)
        assertSame(expected, actual.cause)
    }

    @ParameterizedTest
    @MethodSource("recoverableExceptions")
    fun `Should throw original recoverable exceptions wrapped in CryptoException when executing delete`(e: Throwable) {
        val decorator = createDecorator()
        val alias = UUID.randomUUID().toString()
        val context = emptyMap<String, String>()
        whenever(
            cryptoService.delete(alias, context)
        ).thenAnswer { throw e }
        val actual = assertThrows(CryptoException::class.java) {
            decorator.delete(alias, context)
        }
        assertFalse(actual.isRecoverable)
        assertSame(e, actual.cause)
    }

    @Test
    fun `Should execute deriveSharedSecret`() {
        val decorator = createDecorator()
        val context = emptyMap<String, String>()
        val spec = mock<SharedSecretSpec>()
        decorator.deriveSharedSecret(spec, context)
        Mockito.verify(cryptoService, times(1)).deriveSharedSecret(spec, context)
    }

    @ParameterizedTest
    @MethodSource("mostCommonUnrecoverableExceptions")
    fun `Should throw same non recoverable exception when executing deriveSharedSecret`(expected: Throwable) {
        val decorator = createDecorator()
        val spec = mock<SharedSecretSpec>()
        val context = emptyMap<String, String>()
        whenever(
            cryptoService.deriveSharedSecret(spec, context)
        ).thenAnswer { throw expected }
        val actual = assertThrows(expected::class.java) {
            decorator.deriveSharedSecret(spec, context)
        }
        assertSame(expected, actual)
    }

    @ParameterizedTest
    @MethodSource("mostCommonCheckedExceptions")
    fun `Should throw checked exception wrapped in CryptoException when executing deriveSharedSecret`(
        expected: Throwable
    ) {
        val decorator = createDecorator()
        val spec = mock<SharedSecretSpec>()
        val context = emptyMap<String, String>()
        whenever(
            cryptoService.deriveSharedSecret(spec, context)
        ).thenAnswer { throw expected }
        val actual = assertThrows(CryptoException::class.java) {
            decorator.deriveSharedSecret(spec, context)
        }
        assertFalse(actual.isRecoverable)
        assertSame(expected, actual.cause)
    }

    @ParameterizedTest
    @MethodSource("recoverableExceptions")
    fun `Should throw original recoverable exceptions wrapped in CryptoException when executing deriveSharedSecret`(
        e: Throwable
    ) {
        val decorator = createDecorator()
        val spec = mock<SharedSecretSpec>()
        val context = emptyMap<String, String>()
        whenever(
            cryptoService.deriveSharedSecret(spec, context)
        ).thenAnswer { throw e }
        val actual = assertThrows(CryptoException::class.java) {
            decorator.deriveSharedSecret(spec, context)
        }
        assertFalse(actual.isRecoverable)
        assertSame(e, actual.cause)
    }

    @Test
    fun `Should throw TimeoutException wrapped into CryptoRetryException on timeout`() {
        val decorator = createDecorator(0)
        val data = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val spec = mock<SigningSpec>()
        val expected = UUID.randomUUID().toString().toByteArray()
        whenever(
            cryptoService.sign(spec, data, context)
        ).thenAnswer(AnswersWithDelay(500, Returns(expected)))
        val e = assertThrows(CryptoRetryException::class.java) {
            decorator.sign(spec, data, context)
        }
        assertThat(e.cause).isInstanceOf(TimeoutException::class.java)
    }

    @Test
    fun `Should throw CryptoRetryException on exceeding number of retries`() {
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
        val e = assertThrows(CryptoException::class.java) {
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
            .thenThrow(
                CryptoThrottlingException.createExponential(
                    message = "error",
                    initialBackoff = 100,
                    maxAttempts = 3,
                    cause = IllegalStateException()
                )
            )
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
        val e = CryptoThrottlingException.createExponential(
            message = "error",
            initialBackoff = 100,
            maxAttempts = 3,
            cause = IllegalStateException()
        )
        whenever(
            cryptoService.sign(spec, data, context)
        )
            .thenThrow(e, e, e)
        assertThrows(CryptoException::class.java) {
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