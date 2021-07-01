package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.IdentityType
import org.slf4j.Logger

interface SessionManager {
    fun setLogger(newLogger: Logger)
    fun getInitiatorSession(key: SessionKey): Session?
    fun getResponderSession(uuid: String): Session?
    fun processSessionMessage(message: LinkInMessage): LinkOutMessage?
    fun getSessionInitMessage(sessionKey: SessionKey): LinkOutMessage?

    //On the Initiator side there is a single unique session per SessionKey.
    data class SessionKey(val ourGroupId: String, val ourType: IdentityType, val responderId: LinkManagerNetworkMap.HoldingIdentity)
}