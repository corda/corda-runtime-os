package net.corda.membership.synchronisation

import net.corda.data.membership.command.synchronisation.mgm.ProcessSyncRequest
import net.corda.messaging.api.records.Record

/**
 * Handles the membership data synchronisation process on the mgm side.
 */
interface MgmSynchronisationService : SynchronisationService {
    /**
     * Processes the sync requests coming from members, collects the missing data based on the content of the request
     * and sends it back to the given member.
     *
     * @param request The sync request which needs to be processed.
     * @return list of records to post to the message bus
     */
    fun processSyncRequest(request: ProcessSyncRequest): List<Record<*, *>>
}
