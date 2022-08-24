package net.corda.membership.synchronisation

import net.corda.data.membership.command.synchronisation.member.ProcessMembershipUpdates
import net.corda.data.membership.command.synchronisation.mgm.ProcessSyncRequest
import net.corda.lifecycle.Lifecycle
import net.corda.membership.lib.exceptions.SynchronisationProtocolSelectionException
import net.corda.membership.lib.exceptions.SynchronisationProtocolTypeException

/**
 * Proxy for data synchronisation: passes the sync commands for further processing to the appropriate [SynchronisationService].
 * Synchronisation requests will be handled by an mgm implementation and membership updates by a
 * member implementation
 * Implementations of this interface must coordinate lifecycles of all mgm and member implementations.
 */
interface SynchronisationProxy : Lifecycle {
    /**
     * Retrieves the appropriate instance of [MemberSynchronisationService] for a holding identity as specified in the CPI
     * configuration, and delegates the processing of membership updates to it.
     *
     * @param updates Data package distributed by the MGM containing membership updates.
     *
     * @throws [SynchronisationProtocolSelectionException] if the synchronisation protocol could not be selected.
     * @throws [SynchronisationProtocolTypeException] if the configured protocol is not an [MemberSynchronisationService].
     */
    fun processMembershipUpdates(updates: ProcessMembershipUpdates)

    /**
     * Retrieves the appropriate instance of [MgmSynchronisationService] for a holding identity as specified in the CPI
     * configuration, and delegates the processing of membership sync requests to it.
     *
     * @param request The sync request which needs to be processed.
     *
     * @throws [SynchronisationProtocolSelectionException] if the synchronisation protocol could not be selected.
     * @throws [SynchronisationProtocolTypeException] if the configured protocol is not an [MgmSynchronisationService].
     */
    fun processSyncRequest(request: ProcessSyncRequest)
}
