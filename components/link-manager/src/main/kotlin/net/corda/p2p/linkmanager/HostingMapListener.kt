package net.corda.p2p.linkmanager

import net.corda.data.identity.HoldingIdentity

interface HostingMapListener {
    data class IdentityInfo(
        val holdingIdentity: HoldingIdentity,
        val tlsCertificates: List<PemCertificates>,
        val tenantId: String,
    )
    fun identityAdded(identityInfo: IdentityInfo)
}
