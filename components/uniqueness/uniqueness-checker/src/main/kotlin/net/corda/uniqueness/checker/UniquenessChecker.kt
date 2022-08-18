package net.corda.uniqueness.checker

import net.corda.data.uniqueness.UniquenessCheckExternalRequest
import net.corda.data.uniqueness.UniquenessCheckExternalResponse
import net.corda.lifecycle.Lifecycle

/**
 * Interface for the uniqueness checking component, which performs uniqueness checking against
 * a list of requests and returns a corresponding list of responses.
 *
 * See [UniquenessCheckExternalRequest] and [UniquenessCheckExternalResponse] for details of message formats.
 */
interface UniquenessChecker : Lifecycle {
    fun processRequests(requests: List<UniquenessCheckExternalRequest>): List<UniquenessCheckExternalResponse>
}
