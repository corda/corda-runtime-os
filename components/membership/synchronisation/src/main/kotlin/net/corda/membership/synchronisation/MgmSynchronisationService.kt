package net.corda.membership.synchronisation

import net.corda.data.membership.command.synchronisation.mgm.ProcessSyncRequest

/**
 * Handles the membership data synchronisation process on the mgm side.
 */
interface MgmSynchronisationService : SynchronisationService {
    fun processSyncRequest(request: ProcessSyncRequest)
}