package net.corda.ledger.verification.processor.impl

import net.corda.flow.external.events.responses.exceptions.CpkNotAvailableException
import net.corda.flow.external.events.responses.exceptions.NotAllowedCpkException
import net.corda.flow.external.events.responses.exceptions.criteria
import net.corda.ledger.verification.processor.VerificationErrorType
import net.corda.ledger.verification.processor.VerificationExceptionCategorizer
import java.io.NotSerializableException

class VerificationExceptionCategorizerImpl : VerificationExceptionCategorizer {
    override fun categorize(exception: Throwable): VerificationErrorType {
        return when {
            isPossiblyFatal(exception) -> VerificationErrorType.RETRYABLE
            isPlatform(exception) -> VerificationErrorType.PLATFORM
            // Add transient and fatal here when some exist.
            else -> VerificationErrorType.PLATFORM
        }
    }

    private fun isPossiblyFatal(exception: Throwable): Boolean {
        val checks = listOf(
            // Treat as potentially fatal as bounded retries are desirable if the CPK isn't available.
            criteria<CpkNotAvailableException>()
        )
        return checks.any { it.meetsCriteria(exception) }
    }

    private fun isPlatform(exception: Throwable): Boolean {
        val checks = listOf(
            criteria<NotAllowedCpkException>(),
            criteria<NotSerializableException>()
        )
        return checks.any { it.meetsCriteria(exception) }
    }
}
