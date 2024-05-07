package net.corda.crypto.service.impl

import net.corda.crypto.service.CryptoExceptionType
import net.corda.db.connection.manager.DBConfigurationException
import net.corda.orm.PersistenceExceptionCategorizer
import net.corda.orm.PersistenceExceptionType
import net.corda.v5.crypto.exceptions.CryptoException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class CryptoExceptionCategorizerImplTest {

    private companion object {

        @JvmStatic
        fun fatalExceptions() : Stream<Arguments> {
            return Stream.of(
                Arguments.of(DBConfigurationException("foo"))
            )
        }

        @JvmStatic
        fun platformExceptions(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(IllegalArgumentException()),
                Arguments.of(IllegalStateException()),
                Arguments.of(CryptoException("foo", false))
            )
        }

        @JvmStatic
        fun transientExceptions(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(CryptoException("bar", true))
            )
        }
    }

    // Default persistence categorizer for showing correct handling within crypto categorizer.
    private val persistenceExceptionCategorizer = object : PersistenceExceptionCategorizer {
        override fun categorize(exception: Exception): PersistenceExceptionType {
            return PersistenceExceptionType.UNCATEGORIZED
        }
    }

    @ParameterizedTest
    @MethodSource("fatalExceptions")
    fun `categorizes fatal exceptions as fatal`(exception: Exception) {
        val categorizer = CryptoExceptionCategorizerImpl(persistenceExceptionCategorizer)
        val value = categorizer.categorize(exception)
        assertThat(value).isEqualTo(CryptoExceptionType.FATAL)
    }

    @ParameterizedTest
    @MethodSource("platformExceptions")
    fun `categorizes platform exceptions as platform`(exception: Exception) {
        val categorizer = CryptoExceptionCategorizerImpl(persistenceExceptionCategorizer)
        val value = categorizer.categorize(exception)
        assertThat(value).isEqualTo(CryptoExceptionType.PLATFORM)
    }

    @ParameterizedTest
    @MethodSource("transientExceptions")
    fun `categorizes transient exceptions as transient`(exception: Exception) {
        val categorizer = CryptoExceptionCategorizerImpl(persistenceExceptionCategorizer)
        val value = categorizer.categorize(exception)
        assertThat(value).isEqualTo(CryptoExceptionType.TRANSIENT)
    }

    @Test
    fun `fatal persistence exception results in fatal crypto exception`() {
        val persistenceCategorizer = object : PersistenceExceptionCategorizer {
            override fun categorize(exception: Exception): PersistenceExceptionType {
                return PersistenceExceptionType.FATAL
            }
        }
        val categorizer = CryptoExceptionCategorizerImpl(persistenceCategorizer)
        val result = categorizer.categorize(Exception("foo"))
        assertThat(result).isEqualTo(CryptoExceptionType.FATAL)
    }

    @Test
    fun `data related persistence exception results in platform crypto exception`() {
        val persistenceCategorizer = object : PersistenceExceptionCategorizer {
            override fun categorize(exception: Exception): PersistenceExceptionType {
                return PersistenceExceptionType.DATA_RELATED
            }
        }
        val categorizer = CryptoExceptionCategorizerImpl(persistenceCategorizer)
        val result = categorizer.categorize(Exception("foo"))
        assertThat(result).isEqualTo(CryptoExceptionType.PLATFORM)
    }

    @Test
    fun `transient persistence exception results in transient crypto exception`() {
        val persistenceCategorizer = object : PersistenceExceptionCategorizer {
            override fun categorize(exception: Exception): PersistenceExceptionType {
                return PersistenceExceptionType.TRANSIENT
            }
        }
        val categorizer = CryptoExceptionCategorizerImpl(persistenceCategorizer)
        val result = categorizer.categorize(Exception("foo"))
        assertThat(result).isEqualTo(CryptoExceptionType.TRANSIENT)
    }

    @Test
    fun `defaults exception category to platform`() {
        val categorizer = CryptoExceptionCategorizerImpl(persistenceExceptionCategorizer)
        val value = categorizer.categorize(Exception("bar"))
        assertThat(value).isEqualTo(CryptoExceptionType.PLATFORM)
    }
}