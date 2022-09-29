package net.corda.membership.p2p.helpers

import net.corda.crypto.client.CryptoOpsClient
import java.security.PublicKey

class Signer(
    tenantId: String,
    publicKey: PublicKey,
    cryptoOpsClient: CryptoOpsClient,
) : CryptoAction(
    tenantId,
    publicKey,
    cryptoOpsClient,
) {
    fun sign(data: ByteArray) =
        cryptoOpsClient.sign(
            tenantId = tenantId,
            publicKey = publicKey,
            data = data,
            signatureSpec = spec,
        )
}
