package net.corda.p2p.linkmanager

import net.corda.p2p.NetworkType
import net.corda.p2p.crypto.ProtocolMode

interface NetworkMapListener {
    data class GroupInfo(
        val groupId: String,
        val networkType: NetworkType,
        val protocolModes: Set<ProtocolMode>,
        val trustedCertificates: List<PemCertificates>,
    )
    fun groupAdded(groupInfo: GroupInfo)
}
