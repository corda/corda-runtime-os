package net.corda.p2p.linkmanager.state

import net.corda.data.p2p.event.SessionDirection
import net.corda.libs.statemanager.api.State
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata.Companion.isOutbound

fun State.direction(): SessionDirection {
    return if (metadata.isOutbound()) {
        SessionDirection.OUTBOUND
    } else {
        SessionDirection.INBOUND
    }
}
