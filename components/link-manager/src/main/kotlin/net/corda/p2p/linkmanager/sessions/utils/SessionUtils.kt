package net.corda.p2p.linkmanager.sessions.utils

import net.corda.libs.statemanager.api.State
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata.Companion.toOutbound

internal fun getSessionCounterpartiesFromState(state: State): SessionManager.SessionCounterparties {
        val metadata = state.metadata.toOutbound()
        return SessionManager.SessionCounterparties(
            metadata.commonData.source,
            metadata.commonData.destination,
            metadata.membershipStatus,
            metadata.serial,
            metadata.communicationWithMgm,
        )
}