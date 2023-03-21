package net.corda.p2p.linkmanager.hosting

import net.corda.crypto.utils.PemCertificate
import net.corda.virtualnode.HoldingIdentity
import java.security.PublicKey

interface HostingMapListener {
    data class IdentityInfo(
        val holdingIdentity: HoldingIdentity,
        val tlsCertificates: List<PemCertificate>,
        val tlsTenantId: String,
        val preferredSessionKey: SessionKey,
    )

    data class SessionKey(
        val sessionPublicKey: PublicKey,
        val sessionCertificates: List<PemCertificate>?
    )
    fun identityAdded(identityInfo: IdentityInfo)
}
