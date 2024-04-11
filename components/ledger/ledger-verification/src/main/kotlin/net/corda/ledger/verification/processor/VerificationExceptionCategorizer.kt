package net.corda.ledger.verification.processor

import net.corda.data.flow.event.external.ExternalEventResponseErrorType

fun interface VerificationExceptionCategorizer {
    fun categorize(exception: Throwable) : ExternalEventResponseErrorType
}