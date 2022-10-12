package net.corda.p2p.linkmanager

import net.corda.membership.lib.grouppolicy.GroupPolicyConstants
import net.corda.p2p.NetworkType
import net.corda.p2p.crypto.ProtocolMode
import net.corda.virtualnode.HoldingIdentity
import java.security.KeyStore

interface GroupPolicyListener {
    data class GroupInfo(
        val holdingIdentity: HoldingIdentity,
        val networkType: NetworkType,
        val protocolModes: Set<ProtocolMode>,
        val trustedCertificates: List<PemCertificates>,
        val sessionPkiMode: GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode,
        val sessionTrustStore: KeyStore?
    )
    fun groupAdded(groupInfo: GroupInfo)
}
