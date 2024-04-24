package net.corda.membership.locally.hosted.identities

import net.corda.data.p2p.HostedIdentityEntry
import net.corda.lifecycle.Lifecycle
import net.corda.reconciliation.ReconcilerReader
import net.corda.virtualnode.HoldingIdentity

interface LocallyHostedIdentitiesService : ReconcilerReader<String, HostedIdentityEntry>, Lifecycle {
    /**
     * Returns [true] if an identity is locally hosted. This shouldn't be used before the [identity] has been registered.
     */
    fun isHostedLocally(identity: HoldingIdentity): Boolean

    /**
     * Returns information about a locally hosted identity. If the identity is not yet available from the message bus then the function
     * polls a few times, to see if it gets published. Hence, this function should ONLY be used when [identity] is known to be locally
     * hosted and NOT used by performance critical code. Returns null if the information hasn't been published or the identity is not
     * locally hosted.
     */
    fun pollForIdentityInfo(identity: HoldingIdentity): IdentityInfo?
}
