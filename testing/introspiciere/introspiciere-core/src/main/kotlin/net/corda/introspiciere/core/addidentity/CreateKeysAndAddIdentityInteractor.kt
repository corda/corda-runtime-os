package net.corda.introspiciere.core.addidentity

import net.corda.introspiciere.core.Interactor
import net.corda.p2p.test.KeyAlgorithm
import net.corda.p2p.test.KeyAlgorithm.ECDSA
import net.corda.p2p.test.KeyAlgorithm.RSA
import net.corda.p2p.test.KeyPairEntry
import java.nio.ByteBuffer
import java.security.KeyPairGenerator

class CreateKeysAndAddIdentityInteractor(private val cryptoKeySender: CryptoKeySender) :
    Interactor<CreateKeysAndAddIdentityInteractor.Input> {

    data class Input(
        val alias: String,
        val algorithm: KeyAlgorithm,
        val keySize: Int = defaultKeySize(algorithm),
    ) {
        companion object {
            private fun defaultKeySize(alg: KeyAlgorithm): Int = when (alg) {
                ECDSA -> 571
                RSA -> 2048
            }
        }
    }

    override fun execute(input: Input) {
        val generator = KeyPairGenerator.getInstance(
            if (input.algorithm == ECDSA) "EC" else input.algorithm.toString()
        )
        generator.initialize(input.keySize)
        val pair = generator.generateKeyPair()
        val keyPairEntry = KeyPairEntry(
            input.algorithm,
            ByteBuffer.wrap(pair.public.encoded),
            ByteBuffer.wrap(pair.private.encoded)
        )
        cryptoKeySender.send(input.alias, keyPairEntry)
    }
}

