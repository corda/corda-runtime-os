package net.corda.ledger.libs.uniqueness

import net.corda.ledger.libs.uniqueness.data.UniquenessCheckRequest
import net.corda.ledger.libs.uniqueness.data.UniquenessCheckResponse

interface UniquenessChecker {
    fun processRequests(
        requests: List<UniquenessCheckRequest>
    ): Map<UniquenessCheckRequest, UniquenessCheckResponse>
}
