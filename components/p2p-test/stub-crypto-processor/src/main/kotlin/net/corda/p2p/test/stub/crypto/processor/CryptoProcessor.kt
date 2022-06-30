package net.corda.p2p.test.stub.crypto.processor

import net.corda.lifecycle.domino.logic.NamedLifecycle
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

interface CryptoProcessor {
    fun sign(
        tenantId: String,
        publicKey: PublicKey,
        spec: SignatureSpec,
        data: ByteArray
    ): ByteArray

    val namedLifecycle: NamedLifecycle
}
