package net.corda.membership.p2p.helpers

import net.corda.crypto.client.CryptoOpsClient
import java.security.PublicKey

class Signer(
    private val tenantId: String,
    private val publicKey: PublicKey,
    private val cryptoOpsClient: CryptoOpsClient,
) {
    val signatureSpec by lazy {
        val keySpecExtractor = KeySpecExtractor(
            tenantId,
            cryptoOpsClient,
        )
        keySpecExtractor.getSpec(publicKey)
    }

    fun sign(data: ByteArray) =
        cryptoOpsClient.sign(
            tenantId = tenantId,
            publicKey = publicKey,
            data = data,
            signatureSpec = signatureSpec
        )
}
