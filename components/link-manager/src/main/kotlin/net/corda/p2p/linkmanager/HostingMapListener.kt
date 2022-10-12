package net.corda.p2p.linkmanager

import net.corda.virtualnode.HoldingIdentity
import java.security.PublicKey

interface HostingMapListener {
    data class IdentityInfo(
        val holdingIdentity: HoldingIdentity,
        val tlsCertificates: List<PemCertificates>,
        val tlsTenantId: String,
        val sessionKeyTenantId: String,
        val sessionPublicKey: PublicKey,
        val sessionCertificates: List<PemCertificates>?
    )
    fun identityAdded(identityInfo: IdentityInfo)
}
