package net.corda.crypto.impl.rpc

import net.corda.crypto.FreshKeySigningService
import net.corda.v5.cipher.suite.schemes.SignatureSpec
import net.corda.v5.crypto.DigitalSignature
import java.security.PublicKey
import java.util.UUID

class FreshKeySigningServiceClient : FreshKeySigningService, AutoCloseable {
    override fun freshKey(): PublicKey {
        TODO("Not yet implemented")
    }

    override fun freshKey(externalId: UUID): PublicKey {
        TODO("Not yet implemented")
    }

    override fun sign(publicKey: PublicKey, data: ByteArray): DigitalSignature.WithKey {
        TODO("Not yet implemented")
    }

    override fun sign(publicKey: PublicKey, signatureSpec: SignatureSpec, data: ByteArray): DigitalSignature.WithKey {
        TODO("Not yet implemented")
    }

    override fun ensureWrappingKey() {
        TODO("Not yet implemented")
    }

    override fun filterMyKeys(candidateKeys: Iterable<PublicKey>): Iterable<PublicKey> {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}