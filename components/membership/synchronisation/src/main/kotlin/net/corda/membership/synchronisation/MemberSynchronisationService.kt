package net.corda.membership.synchronisation

import net.corda.data.membership.MembershipPackage
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.virtualnode.HoldingIdentity

/**
 * Handles the membership data synchronisation process on the member side.
 */
interface MemberSynchronisationService : Lifecycle {
    /**
     * Lifecycle coordinator name for implementing services.
     *
     * All implementing services must make use of the optional `instanceId` parameter when creating their
     * [LifecycleCoordinatorName] so that multiple instances of [MemberSynchronisationService] coordinator can be created
     * and followed by the synchronisation provider.
     */
    val lifecycleCoordinatorName: LifecycleCoordinatorName
        get() = LifecycleCoordinatorName(MemberSynchronisationService::class.java.name, this::class.java.name)

    /**
     * TODO
     */
    fun processMembershipUpdates(member: HoldingIdentity, membershipPackage: MembershipPackage)
}
