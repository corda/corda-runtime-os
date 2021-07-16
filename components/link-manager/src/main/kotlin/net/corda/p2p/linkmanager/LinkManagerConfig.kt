package net.corda.p2p.linkmanager

import net.corda.p2p.crypto.ProtocolMode

data class LinkManagerConfig(
    val maxMessageSize: Int,
    val protocolModes: Set<ProtocolMode>
)