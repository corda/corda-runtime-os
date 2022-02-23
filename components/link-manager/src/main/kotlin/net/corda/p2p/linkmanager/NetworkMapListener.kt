package net.corda.p2p.linkmanager

import net.corda.data.identity.HoldingIdentity
import net.corda.p2p.NetworkType

interface NetworkMapListener {
    data class GroupInfo(
        val groupId: String,
        val networkType: NetworkType,
        val trustedCertificates: List<PemCertificates>,
    )
    data class IdentityInfo(
        val holdingIdentity: HoldingIdentity,
        val address: String,
        val tlsCertificates: List<PemCertificates>,
    )
    fun groupAdded(groupInfo: GroupInfo) {}
    fun identityAdded(identityInfo: IdentityInfo) {}
}
