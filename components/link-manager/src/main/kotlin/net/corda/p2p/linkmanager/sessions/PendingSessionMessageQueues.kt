package net.corda.p2p.linkmanager.sessions

import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.p2p.crypto.protocol.api.Session

internal interface PendingSessionMessageQueues : LifecycleWithDominoTile {
    fun queueMessage(message: AuthenticatedMessageAndKey, counterparties: SessionManager.SessionCounterparties)
    fun sessionNegotiatedCallback(
        sessionManager: SessionManager,
        counterparties: SessionManager.SessionCounterparties,
        session: Session,
    )

    fun destroyQueue(counterparties: SessionManager.SessionCounterparties)
    fun destroyAllQueues()
}
