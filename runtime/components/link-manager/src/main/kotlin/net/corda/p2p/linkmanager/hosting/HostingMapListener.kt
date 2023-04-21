package net.corda.p2p.linkmanager.hosting

import net.corda.crypto.utils.PemCertificate
import net.corda.virtualnode.HoldingIdentity
import java.security.PublicKey

interface HostingMapListener {
    data class IdentityInfo(
        val holdingIdentity: HoldingIdentity,
        val tlsCertificates: List<PemCertificate>,
        val tlsTenantId: String,
        val preferredSessionKeyAndCertificates: SessionKeyAndCertificates,
        val alternativeSessionKeysAndCertificates: Collection<SessionKeyAndCertificates>,
    ) {
        val allSessionKeysAndCertificates by lazy {
            listOf(preferredSessionKeyAndCertificates) + alternativeSessionKeysAndCertificates
        }
    }

    data class SessionKeyAndCertificates(
        val sessionPublicKey: PublicKey,
        val sessionCertificateChain: List<PemCertificate>?
    )
    fun identityAdded(identityInfo: IdentityInfo)
}
