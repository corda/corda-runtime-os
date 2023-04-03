package net.corda.membership.p2p.helpers

import net.corda.crypto.client.CryptoOpsClient
import net.corda.membership.lib.MemberInfoExtension.Companion.id
import net.corda.membership.lib.MemberInfoExtension.Companion.sessionInitiationKeys
import net.corda.v5.membership.MemberInfo

class SignerFactory(
    private val cryptoOpsClient: CryptoOpsClient,
) {
    fun createSigner(
        mgm: MemberInfo,
    ): Signer =
        Signer(
            mgm.id,
            // CORE-11935: We should use the preferred key instead of the first one.
            mgm.sessionInitiationKeys.first(),
            cryptoOpsClient,
        )
}
