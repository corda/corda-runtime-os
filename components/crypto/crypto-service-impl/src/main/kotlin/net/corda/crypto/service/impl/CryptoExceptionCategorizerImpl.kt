package net.corda.crypto.service.impl

import net.corda.crypto.core.isRecoverable
import net.corda.crypto.service.CryptoExceptionCategorizer
import net.corda.crypto.service.CryptoExceptionType
import net.corda.crypto.service.CryptoExceptionType.FATAL
import net.corda.crypto.service.CryptoExceptionType.PLATFORM
import net.corda.crypto.service.CryptoExceptionType.TRANSIENT
import net.corda.db.connection.manager.DBConfigurationException
import net.corda.orm.PersistenceExceptionCategorizer
import net.corda.orm.PersistenceExceptionType
import net.corda.utilities.criteria
import net.corda.v5.crypto.exceptions.CryptoException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Component(service = [CryptoExceptionCategorizer::class])
internal class CryptoExceptionCategorizerImpl @Activate constructor(
    @Reference(service = PersistenceExceptionCategorizer::class)
    private val persistenceExceptionCategorizer: PersistenceExceptionCategorizer
) : CryptoExceptionCategorizer {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun categorize(exception: Exception): CryptoExceptionType {
        return when (persistenceExceptionCategorizer.categorize(exception)) {
            PersistenceExceptionType.FATAL -> FATAL
            PersistenceExceptionType.TRANSIENT -> TRANSIENT
            PersistenceExceptionType.DATA_RELATED -> PLATFORM
            PersistenceExceptionType.UNCATEGORIZED -> categorizeCrypto(exception)
        }.also {
            logger.warn("Categorized exception as $it: $exception", exception)
        }
    }

    private fun categorizeCrypto(exception: Exception): CryptoExceptionType {
        return when {
            isFatal(exception) -> FATAL
            isPlatform(exception) -> PLATFORM
            isTransient(exception) -> TRANSIENT
            else -> PLATFORM
        }
    }

    private fun isFatal(exception: Exception): Boolean {
        val checks = listOf(
            criteria<DBConfigurationException>(),
        )

        return checks.any { it.meetsCriteria(exception) }
    }

    private fun isPlatform(exception: Exception): Boolean {
        val checks = listOf(
            criteria<IllegalArgumentException>(),
            criteria<IllegalStateException>(),
            criteria<CryptoException> {
                !exception.isRecoverable()
            }
        )

        return checks.any { it.meetsCriteria(exception) }
    }

    private fun isTransient(exception: Exception): Boolean {
        val checks = listOf(
            criteria<CryptoException> {
                exception.isRecoverable()
            },

        )

        return checks.any { it.meetsCriteria(exception) }
    }
}
