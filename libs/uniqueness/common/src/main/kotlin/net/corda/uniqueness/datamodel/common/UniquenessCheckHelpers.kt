package net.corda.uniqueness.datamodel.common

import net.corda.crypto.core.parseSecureHash
import net.corda.uniqueness.datamodel.impl.UniquenessCheckStateRefImpl
import net.corda.v5.application.uniqueness.model.UniquenessCheckResult
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultSuccess
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef

fun UniquenessCheckResult.toCharacterRepresentation() = if (this is UniquenessCheckResultSuccess) {
    UniquenessConstants.RESULT_ACCEPTED_REPRESENTATION
} else {
    UniquenessConstants.RESULT_REJECTED_REPRESENTATION
}

fun String.toStateRef(): UniquenessCheckStateRef {
    return UniquenessCheckStateRefImpl(
        parseSecureHash(substringBeforeLast(":")),
        substringAfterLast(":").toInt()
    )
}
