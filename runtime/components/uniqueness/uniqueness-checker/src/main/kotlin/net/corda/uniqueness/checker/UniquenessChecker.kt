package net.corda.uniqueness.checker

import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.data.uniqueness.UniquenessCheckResponseAvro
import net.corda.lifecycle.Lifecycle

/**
 * Interface for the uniqueness checking component.
 */
interface UniquenessChecker : Lifecycle {
    /**
     * Performs uniqueness checking against a list of requests and returns a map of requests and
     * their corresponding responses.
     *
     * The ordering of the returned mappings is not guaranteed to match those of the supplied
     * [requests] parameter. Callers should therefore use the request objects in the returned
     * responses if relying on any data stored in the request.
     *
     * See [UniquenessCheckRequestAvro] and [UniquenessCheckResponseAvro] for details of message
     * formats.
     */
    fun processRequests(
        requests: List<UniquenessCheckRequestAvro>
    ): Map<UniquenessCheckRequestAvro, UniquenessCheckResponseAvro>
}
