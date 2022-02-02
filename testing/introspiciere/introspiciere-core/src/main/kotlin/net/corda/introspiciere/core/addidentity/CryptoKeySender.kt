package net.corda.introspiciere.core.addidentity

import net.corda.introspiciere.core.SimpleKafkaClient
import net.corda.p2p.test.KeyPairEntry
import net.corda.schema.TestSchema.Companion.CRYPTO_KEYS_TOPIC

interface CryptoKeySender {
    fun send(alias: String, keyPair: KeyPairEntry)
}

class CryptoKeySenderImpl(private val kafka: SimpleKafkaClient) : CryptoKeySender {
    companion object {
        const val topicName: String = CRYPTO_KEYS_TOPIC
    }

    override fun send(alias: String, keyPair: KeyPairEntry) {
        kafka.send(topicName, alias, keyPair)
    }
}