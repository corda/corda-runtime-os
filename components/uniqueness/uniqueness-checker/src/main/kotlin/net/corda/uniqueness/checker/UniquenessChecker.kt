package net.corda.uniqueness.checker

import net.corda.data.uniqueness.UniquenessCheckRequest
import net.corda.data.uniqueness.UniquenessCheckResponse
import net.corda.lifecycle.Lifecycle

interface UniquenessChecker : Lifecycle {
    fun processRequests(requests: List<UniquenessCheckRequest>) : List<UniquenessCheckResponse>
}
