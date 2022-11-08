package net.corda.p2p.linkmanager

import net.corda.crypto.utils.KeyStoreWithPem
import net.corda.crypto.utils.PemCertificate
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants
import net.corda.p2p.NetworkType
import net.corda.p2p.crypto.ProtocolMode
import net.corda.virtualnode.HoldingIdentity

interface GroupPolicyListener {
    data class GroupInfo(
        val holdingIdentity: HoldingIdentity,
        val networkType: NetworkType,
        val protocolModes: Set<ProtocolMode>,
        val trustedCertificates: List<PemCertificate>,
        val sessionPkiMode: GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode,
        val sessionTrustStore: KeyStoreWithPem?
    )

    fun groupAdded(groupInfo: GroupInfo)
}
