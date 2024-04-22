package net.corda.membership.locally.hosted.identities

import net.corda.data.p2p.HostedIdentityEntry
import net.corda.lifecycle.Lifecycle
import net.corda.reconciliation.ReconcilerWriter
import net.corda.virtualnode.HoldingIdentity

/**
 * Locally hosted identities writer (PUBLISHER) interface.
 */
interface LocallyHostedIdentitiesWriter : ReconcilerWriter<HoldingIdentity, HostedIdentityEntry>, Lifecycle {
    override fun put(recordKey: HoldingIdentity, recordValue: HostedIdentityEntry)

    override fun remove(recordKey: HoldingIdentity)
}
