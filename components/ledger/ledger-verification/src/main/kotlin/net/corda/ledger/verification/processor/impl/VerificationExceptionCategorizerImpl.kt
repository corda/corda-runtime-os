package net.corda.ledger.verification.processor.impl

import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.flow.external.events.responses.exceptions.CpkNotAvailableException
import net.corda.flow.external.events.responses.exceptions.NotAllowedCpkException
import net.corda.ledger.verification.processor.VerificationExceptionCategorizer
import java.io.NotSerializableException

class VerificationExceptionCategorizerImpl : VerificationExceptionCategorizer {
    override fun categorize(exception: Throwable): ExternalEventResponseErrorType {
        return when {
            isFatal(exception) -> ExternalEventResponseErrorType.FATAL
            isPlatform(exception) -> ExternalEventResponseErrorType.PLATFORM
            isTransient(exception) -> ExternalEventResponseErrorType.TRANSIENT
            else -> ExternalEventResponseErrorType.PLATFORM
        }
    }

    private data class ExceptionCriteria<T: Throwable>(val type: Class<T>, val check: (T) -> Boolean = { _ -> true }) {
        fun meetsCriteria(exception: Throwable?) : Boolean {
            if (exception == null) {
                return false
            }
            val meetsCriteria = if (type.isAssignableFrom(exception::class.java)) {
                check(type.cast(exception))
            } else {
                false
            }
            return (meetsCriteria || meetsCriteria(exception.cause))
        }
    }

    private inline fun <reified T: Throwable> criteria(
        noinline check: (T) -> Boolean = { _ -> true }
    ) : ExceptionCriteria<T> = ExceptionCriteria(T::class.java, check)

    private fun isFatal(exception: Throwable) : Boolean {
        val checks = listOf(
            // Treat as fatal as bounded retries are desirable if the CPK isn't available.
            criteria<CpkNotAvailableException>()
        )
        return checks.any { it.meetsCriteria(exception) }
    }

    private fun isPlatform(exception: Throwable) : Boolean {
        val checks = listOf(
            criteria<NotAllowedCpkException>(),
            criteria<NotSerializableException>()
        )
        return checks.any { it.meetsCriteria(exception) }
    }

    private fun isTransient(exception: Throwable) : Boolean {
        return false
    }
}