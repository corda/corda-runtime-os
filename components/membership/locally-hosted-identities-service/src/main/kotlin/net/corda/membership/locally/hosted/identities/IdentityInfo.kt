package net.corda.membership.locally.hosted.identities

import net.corda.virtualnode.HoldingIdentity
import java.security.cert.X509Certificate

data class IdentityInfo(
    val identity: HoldingIdentity,
    val tlsCertificates: List<X509Certificate>,
)
