package net.corda.ledger.lib.impl.stub.signing

import net.corda.v5.application.crypto.SigningService
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

class StubSigningService : SigningService {

    override fun sign(
        bytes: ByteArray,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec
    ): DigitalSignature.WithKeyId {
        TODO("Not yet implemented")
    }

    override fun findMySigningKeys(keys: MutableSet<PublicKey>): MutableMap<PublicKey, PublicKey> {
        TODO("Not yet implemented")
    }

    override fun decodePublicKey(encodedKey: ByteArray): PublicKey {
        TODO("Not yet implemented")
    }

    override fun decodePublicKey(encodedKey: String): PublicKey {
        TODO("Not yet implemented")
    }

    override fun encodeAsByteArray(publicKey: PublicKey): ByteArray {
        TODO("Not yet implemented")
    }

    override fun encodeAsString(publicKey: PublicKey): String {
        TODO("Not yet implemented")
    }
}