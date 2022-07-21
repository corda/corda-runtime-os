package net.corda.membership.synchronisation

import net.corda.data.membership.p2p.MembershipPackage
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorName

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
     * Publishes the member list updates contained in the [membershipPackage] sent by the MGM to the receiving member.
     *
     * @param membershipPackage Data package distributed by the MGM containing membership updates.
     */
    fun processMembershipUpdates(membershipPackage: MembershipPackage)
}
