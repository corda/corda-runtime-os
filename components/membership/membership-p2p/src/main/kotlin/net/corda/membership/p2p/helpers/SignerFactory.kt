package net.corda.membership.p2p.helpers

import net.corda.crypto.client.CryptoOpsClient
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.MemberInfoExtension.Companion.id
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.v5.membership.MemberInfo

class SignerFactory(
    private val cryptoOpsClient: CryptoOpsClient,
    private val locallyHostedIdentitiesService: LocallyHostedIdentitiesService,
) {
    fun createSigner(
        mgm: MemberInfo,
    ): Signer {
        val holdingId = mgm.holdingIdentity
        val hostingMapData = locallyHostedIdentitiesService.pollForIdentityInfo(holdingId)
            ?: throw IllegalStateException("Can not find preferred key of MGM (${mgm.holdingIdentity})")
        return Signer(
            mgm.id,
            hostingMapData.preferredSessionKey,
            cryptoOpsClient,
        )
    }
}
