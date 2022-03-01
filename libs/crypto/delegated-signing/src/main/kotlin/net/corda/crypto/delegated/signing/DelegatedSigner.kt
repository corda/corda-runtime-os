package net.corda.crypto.delegated.signing

import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

interface DelegatedSigner {

    fun sign(
        tenantId: String,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray
    ): ByteArray
}
