package net.corda.membership.synchronisation

import net.corda.data.membership.MembershipPackage
import net.corda.lifecycle.Lifecycle
import net.corda.membership.lib.exceptions.SynchronisationProtocolSelectionException
import net.corda.virtualnode.HoldingIdentity

/**
 * Proxy for requesting data synchronisation and processing updates with the appropriate instance of [MemberSynchronisationService].
 * Implementations of this interface must coordinate lifecycles of all [MemberSynchronisationService]s.
 */
interface SynchronisationProxy : Lifecycle {
    /**
     * Retrieves the appropriate instance of [MemberSynchronisationService] for a holding identity as specified in the CPI
     * configuration, and forwards the synchronisation request to it.
     *
     * @param member The holding identity of the member receiving the membership data package.
     *
     * @return TODO
     *
     * @throws [SynchronisationProtocolSelectionException] when the synchronisation protocol could not be selected.
     */
    fun processMembershipUpdates(member: HoldingIdentity, membershipPackage: MembershipPackage)
}
