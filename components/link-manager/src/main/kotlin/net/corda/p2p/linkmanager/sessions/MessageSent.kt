package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.crypto.protocol.api.Session

internal interface MessageSent {
    fun dataMessageSent(session: Session)
}