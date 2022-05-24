package net.corda.uniqueness.checker

import net.corda.data.uniqueness.UniquenessCheckRequest
import net.corda.data.uniqueness.UniquenessCheckResponse
import net.corda.lifecycle.Lifecycle

/**
 * Interface for the uniqueness checking component, which performs uniqueness checking against
 * a list of requests and returns a corresponding list of responses.
 *
 * See [UniquenessCheckRequest] and [UniquenessCheckResponse] for details of message formats.
 */
interface UniquenessChecker : Lifecycle {
    fun processRequests(requests: List<UniquenessCheckRequest>) : List<UniquenessCheckResponse>
}
