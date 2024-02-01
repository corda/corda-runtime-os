package net.corda.membership.registration

import net.corda.lifecycle.Lifecycle
import net.corda.virtualnode.HoldingIdentity

/**
 * Processor to handle registration requests stuck in processing and move them to declined state.
 */
interface ExpirationProcessor : Lifecycle {
    /**
     * Starts a scheduled task to query for stuck requests and move them to declined state after an expiration date.
     *
     * @param mgm The [HoldingIdentity] of the MGM of the membership group.
     */
    fun scheduleProcessingOfExpiredRequests(mgm: HoldingIdentity)
}
