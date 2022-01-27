package net.corda.introspiciere.core

import net.corda.p2p.test.KeyAlgorithm
import net.corda.p2p.test.KeyPairEntry
import net.corda.schema.TestSchema
import java.nio.ByteBuffer
import java.security.KeyPairGenerator

class CreateKeysAndAddIdentityInteractor(private val kafka: SimpleKafkaClient) :
    Interactor<CreateKeysAndAddIdentityInteractor.Input> {

    data class Input(
        val alias: String,
        val algorithm: String,
        val keySize: Int = 2048,
    )

    override fun execute(input: Input) {
        val generator = KeyPairGenerator.getInstance(input.algorithm)
        generator.initialize(input.keySize)
        val pair = generator.generateKeyPair()
        val keyPairEntry = KeyPairEntry(
            KeyAlgorithm.valueOf(input.alias),
            ByteBuffer.wrap(pair.public.encoded),
            ByteBuffer.wrap(pair.private.encoded)
        )
        kafka.send(TestSchema.CRYPTO_KEYS_TOPIC, input.alias, keyPairEntry)
    }
}