package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.linkmanager.HostingMapListener
import net.corda.p2p.linkmanager.LinkManagerMembershipGroupReader
import java.security.PublicKey

internal interface ProtocolFactory {
    fun createInitiator(sessionId: String, supportedModes: Set<ProtocolMode>, ourMaxMessageSize: Int,
                        ourPublicKey: PublicKey, groupId: String): AuthenticationProtocolInitiator
    fun createResponder(sessionId: String, supportedModes: Set<ProtocolMode>, ourMaxMessageSize: Int)
        : AuthenticationProtocolResponder<Pair<HostingMapListener.IdentityInfo, LinkManagerMembershipGroupReader.MemberInfo>>
}