package net.corda.membership.impl.registration.dynamic.mgm.handler.helpers

import net.corda.crypto.client.CryptoOpsClient
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.id
import net.corda.v5.membership.MemberInfo

internal class SignerFactory(
    private val cryptoOpsClient: CryptoOpsClient,
) {
    fun createSigner(
        mgm: MemberInfo,
    ): Signer =
        Signer(
            mgm.id,
            mgm.sessionInitiationKey,
            cryptoOpsClient,
        )
}
