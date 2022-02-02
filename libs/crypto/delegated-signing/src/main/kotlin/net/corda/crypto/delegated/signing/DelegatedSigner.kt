package net.corda.crypto.delegated.signing

import java.security.PublicKey

interface DelegatedSigner {
    fun sign(publicKey: PublicKey, parameter: SigningParameter, data: ByteArray): ByteArray
}
