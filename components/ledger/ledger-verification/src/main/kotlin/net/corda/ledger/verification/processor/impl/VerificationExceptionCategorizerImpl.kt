package net.corda.ledger.verification.processor.impl

import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.flow.external.events.responses.exceptions.CpkNotAvailableException
import net.corda.flow.external.events.responses.exceptions.NotAllowedCpkException
import net.corda.flow.external.events.responses.exceptions.criteria
import net.corda.ledger.verification.processor.VerificationExceptionCategorizer
import java.io.NotSerializableException

class VerificationExceptionCategorizerImpl : VerificationExceptionCategorizer {
    override fun categorize(exception: Throwable): ExternalEventResponseErrorType {
        return when {
            // Rethrow things where we know it might fail, but we want some retry.
            isPossiblyFatal(exception) -> throw exception
            isPlatform(exception) -> ExternalEventResponseErrorType.PLATFORM
            // Add transient here when some exist.
            else -> ExternalEventResponseErrorType.PLATFORM
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
