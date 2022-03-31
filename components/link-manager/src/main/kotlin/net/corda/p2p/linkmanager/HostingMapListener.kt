package net.corda.p2p.linkmanager

import net.corda.data.identity.HoldingIdentity
import java.security.PublicKey

interface HostingMapListener {
    data class IdentityInfo(
        val holdingIdentity: HoldingIdentity,
        val tlsCertificates: List<PemCertificates>,
        val tlsTenantId: String,
        val sessionKeyTenantId: String,
        val sessionPublicKey: PublicKey,
    )
    fun identityAdded(identityInfo: IdentityInfo)
}
