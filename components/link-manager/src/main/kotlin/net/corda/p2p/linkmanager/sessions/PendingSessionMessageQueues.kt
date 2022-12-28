package net.corda.p2p.linkmanager.sessions

import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.p2p.AuthenticatedMessageAndKey
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.membership.LinkManagerMembershipGroupReader

internal interface PendingSessionMessageQueues : LifecycleWithDominoTile {
    fun queueMessage(message: AuthenticatedMessageAndKey)
    fun sessionNegotiatedCallback(
        sessionManager: SessionManager,
        counterparties: SessionManager.SessionCounterparties,
        session: Session,
        groupPolicyProvider: GroupPolicyProvider,
        members: LinkManagerMembershipGroupReader,
    )

    fun destroyQueue(counterparties: SessionManager.SessionCounterparties)
    fun destroyAllQueues()
}
