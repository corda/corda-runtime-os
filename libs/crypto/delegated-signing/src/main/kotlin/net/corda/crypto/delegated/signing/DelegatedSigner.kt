package net.corda.crypto.delegated.signing

import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

interface DelegatedSigner {
    fun logger(log: String) {

    }

    fun sign(
        publicKey: PublicKey,
        spec: SignatureSpec,
        data: ByteArray
    ): ByteArray
}
