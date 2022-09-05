package net.corda.uniqueness.checker

import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResponseAvro
import net.corda.lifecycle.Lifecycle

/**
 * Interface for the uniqueness checking component, which performs uniqueness checking against
 * a list of requests and returns a corresponding list of responses.
 *
 * See [UniquenessCheckRequestAvro] and [UniquenessCheckResponseAvro] for details of message formats.
 */
interface UniquenessChecker : Lifecycle {
    fun processRequests(requests: List<UniquenessCheckRequestAvro>): List<UniquenessCheckResponseAvro>
}
