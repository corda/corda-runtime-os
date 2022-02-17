package net.corda.p2p.linkmanager

import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.p2p.NetworkType

interface NetworkMapListener : LifecycleWithDominoTile {
    data class GroupInfo(
        val groupId: String,
        val networkType: NetworkType,
        val trustedCertificates: List<PemCertificates>,
    )
    fun groupAdded(groupInfo: GroupInfo)
}
