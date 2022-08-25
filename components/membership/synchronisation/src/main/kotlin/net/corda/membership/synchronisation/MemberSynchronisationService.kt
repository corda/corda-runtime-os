package net.corda.membership.synchronisation

import net.corda.data.membership.command.synchronisation.member.ProcessMembershipUpdates

/**
 * Handles the membership data synchronisation process on the member side.
 */
interface MemberSynchronisationService : SynchronisationService {
    /**
     * Publishes the member list updates contained in the [updates] sent by the MGM to the receiving member.
     *
     * @param updates Data package distributed by the MGM containing membership updates.
     */
    fun processMembershipUpdates(updates: ProcessMembershipUpdates)
}
