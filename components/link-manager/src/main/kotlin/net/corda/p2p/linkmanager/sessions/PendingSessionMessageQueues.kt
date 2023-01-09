package net.corda.p2p.linkmanager.sessions

import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.p2p.AuthenticatedMessageAndKey
import net.corda.p2p.crypto.protocol.api.Session

internal interface PendingSessionMessageQueues : LifecycleWithDominoTile {
    fun queueMessage(message: AuthenticatedMessageAndKey)
    fun sessionNegotiatedCallback(
        sessionManager: SessionManager,
        counterparties: SessionManager.SessionCounterparties,
        session: Session,
    )

    fun destroyQueue(counterparties: SessionManager.SessionCounterparties)
    fun destroyAllQueues()
}
