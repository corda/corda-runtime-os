package net.corda.membership.locally.hosted.identities

import net.corda.data.p2p.HostedIdentityEntry
import net.corda.lifecycle.Lifecycle
import net.corda.reconciliation.ReconcilerWriter

/**
 * Locally hosted identities writer (PUBLISHER) interface.
 */
interface LocallyHostedIdentitiesWriter : ReconcilerWriter<String, HostedIdentityEntry>, Lifecycle {
    override fun put(recordKey: String, recordValue: HostedIdentityEntry)

    override fun remove(recordKey: String)
}
