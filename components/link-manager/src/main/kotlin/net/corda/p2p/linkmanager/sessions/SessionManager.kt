package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.crypto.protocol.api.Session
import org.slf4j.Logger

interface SessionManager {
    fun setLogger(newLogger: Logger)
    fun getInitiatorSession(key: SessionManagerImpl.SessionKey): Session?
    fun getResponderSession(uuid: String): Session?
    fun processSessionMessage(message: LinkInMessage): LinkOutMessage?
    fun getSessionInitMessage(sessionKey: SessionManagerImpl.SessionKey): LinkOutMessage?
}