package net.corda.p2p.linkmanager.delivery

import net.corda.p2p.linkmanager.LinkManagerNetworkMap

interface SessionReplayer {

    data class SessionMessageReplay(
        val message: Any,
        val dest: LinkManagerNetworkMap.HoldingIdentity
    )

    fun addMessageForReplay(uniqueId: String, messageReplay: SessionMessageReplay)

    fun removeMessageFromReplay(uniqueId: String)

}