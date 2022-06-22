package net.corda.p2p.linkmanager

import net.corda.data.identity.HoldingIdentity
import net.corda.p2p.NetworkType
import net.corda.p2p.crypto.ProtocolMode

interface GroupPolicyListener {
    data class GroupInfo(
        val holdingIdentity: HoldingIdentity,
        val networkType: NetworkType,
        val protocolModes: Set<ProtocolMode>,
        val trustedCertificates: List<PemCertificates>,
    )
    fun groupAdded(groupInfo: GroupInfo)
}
