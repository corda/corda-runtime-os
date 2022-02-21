package net.corda.p2p.linkmanager

import net.corda.p2p.NetworkType

interface NetworkMapListener {
    data class GroupInfo(
        val groupId: String,
        val networkType: NetworkType,
        val trustedCertificates: List<PemCertificates>,
    )
    fun groupAdded(groupInfo: GroupInfo) {}
}
