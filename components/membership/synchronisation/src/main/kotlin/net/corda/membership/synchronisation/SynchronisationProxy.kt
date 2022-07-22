package net.corda.membership.synchronisation

import net.corda.data.membership.command.synchronisation.member.ProcessMembershipUpdates
import net.corda.lifecycle.Lifecycle
import net.corda.membership.lib.exceptions.SynchronisationProtocolSelectionException

/**
 * Proxy for requesting data synchronisation and processing membership updates with the appropriate instance of
 * [MemberSynchronisationService].
 * Implementations of this interface must coordinate lifecycles of all [MemberSynchronisationService]s.
 */
interface SynchronisationProxy : Lifecycle {
    /**
     * Retrieves the appropriate instance of [MemberSynchronisationService] for a holding identity as specified in the CPI
     * configuration, and delegates the processing of membership updates to it.
     *
     * @param updates Data package distributed by the MGM containing membership updates.
     *
     * @throws [SynchronisationProtocolSelectionException] if the synchronisation protocol could not be selected.
     */
    fun processMembershipUpdates(updates: ProcessMembershipUpdates)
}
