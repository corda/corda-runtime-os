package net.corda.membership.locally.hosted.identities

import net.corda.lifecycle.Lifecycle
import net.corda.virtualnode.HoldingIdentity

interface LocallyHostedIdentitiesService : Lifecycle {
    fun getIdentityInfo(identity: HoldingIdentity) : IdentityInfo?
}