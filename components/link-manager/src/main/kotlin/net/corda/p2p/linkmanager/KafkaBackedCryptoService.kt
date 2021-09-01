package net.corda.p2p.linkmanager

import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.ECDSA_SIGNATURE_ALGO
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.RSA_SIGNATURE_ALGO
import net.corda.p2p.schema.Schema
import net.corda.p2p.test.KeyAlgorithm
import net.corda.p2p.test.KeyPairEntry
import net.corda.v5.base.util.contextLogger
import java.lang.IllegalStateException
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class KafkaBackedCryptoService(subscriptionFactory: SubscriptionFactory): LinkManagerCryptoService, Lifecycle {

    private val keyPairEntryProcessor = KeyPairEntryProcessor()
    private val subscriptionConfig = SubscriptionConfig("crypto-service", Schema.CRYPTO_KEYS_TOPIC)
    private val subscription =
        subscriptionFactory.createCompactedSubscription(subscriptionConfig, keyPairEntryProcessor)

    private val rsaSignature = Signature.getInstance(RSA_SIGNATURE_ALGO)
    private val ecdsaSignature = Signature.getInstance(ECDSA_SIGNATURE_ALGO)

    private val lock = ReentrantReadWriteLock()
    @Volatile
    private var running: Boolean = false

    override val isRunning: Boolean
        get() = running

    override fun start() {
        lock.write {
            if (!running) {
                subscription.start()
                running = true
            }
        }
    }

    override fun stop() {
        lock.write {
            if (running) {
                subscription.stop()
                running = false
            }
        }
    }

    override fun signData(publicKey: PublicKey, data: ByteArray): ByteArray {
        lock.read {
            if (!running) {
                throw IllegalStateException("signData operation invoked while component was stopped.")
            }

            val (privateKey, keyAlgo) = keyPairEntryProcessor.getPrivateKey(publicKey)
                ?: throw LinkManagerCryptoService.NoPrivateKeyForGroupException(publicKey)

            return when (keyAlgo) {
                KeyAlgorithm.RSA -> {
                    synchronized(rsaSignature) {
                        rsaSignature.initSign(privateKey)
                        rsaSignature.update(data)
                        rsaSignature.sign()
                    }
                }
                KeyAlgorithm.ECDSA -> {
                    synchronized(ecdsaSignature) {
                        ecdsaSignature.initSign(privateKey)
                        ecdsaSignature.update(data)
                        ecdsaSignature.sign()
                    }
                }
            }
        }
    }

    private class KeyPairEntryProcessor: CompactedProcessor<String, KeyPairEntry> {

        companion object {
            val logger = contextLogger()
        }

        private val keys = mutableMapOf<String, KeyPair>()

        private val rsaKeyFactory = KeyFactory.getInstance("RSA")
        private val ecdsaKeyFactory = KeyFactory.getInstance("EC")

        override val keyClass: Class<String>
            get() = String::class.java
        override val valueClass: Class<KeyPairEntry>
            get() = KeyPairEntry::class.java

        fun getPrivateKey(publicKey: PublicKey): Pair<PrivateKey, KeyAlgorithm>? {
            val matchedKeys = keys.filterValues { it.publicKey == publicKey }

            if (matchedKeys.isEmpty()) {
                logger.warn("No private key found for public key: ${publicKey.encoded}")
                return null
            }

            if (matchedKeys.size > 1) {
                logger.warn("Multiple keys found for public key: ${publicKey.encoded}. Using first ...")
            }

            val entry = matchedKeys.entries.first().value
            return entry.privateKey to entry.keyAlgo
        }

        override fun onSnapshot(currentData: Map<String, KeyPairEntry>) {
            currentData.forEach { (alias, keyPairEntry) ->
                val privateKey = toPrivateKey(keyPairEntry.privateKey.array(), keyPairEntry.keyAlgo)
                val publicKey = toPublicKey(keyPairEntry.publicKey.array(), keyPairEntry.keyAlgo)
                keys[alias] = KeyPair(keyPairEntry.keyAlgo, privateKey, publicKey)
            }
        }

        override fun onNext(newRecord: Record<String, KeyPairEntry>,
                            oldValue: KeyPairEntry?,
                            currentData: Map<String, KeyPairEntry>) {
            if (newRecord.value == null) {
                keys.remove(newRecord.key)
            } else {
                val keyPairEntry = newRecord.value!!
                val privateKey = toPrivateKey(keyPairEntry.privateKey.array(), keyPairEntry.keyAlgo)
                val publicKey = toPublicKey(keyPairEntry.publicKey.array(), keyPairEntry.keyAlgo)
                keys[newRecord.key] = KeyPair(keyPairEntry.keyAlgo, privateKey, publicKey)
            }
        }

        private fun toPrivateKey(bytes: ByteArray, keyAlgorithm: KeyAlgorithm): PrivateKey {
            return when (keyAlgorithm) {
                KeyAlgorithm.ECDSA -> {
                    ecdsaKeyFactory.generatePrivate(PKCS8EncodedKeySpec(bytes))
                }
                KeyAlgorithm.RSA -> {
                    rsaKeyFactory.generatePrivate(PKCS8EncodedKeySpec(bytes))
                }
            }
        }

        private fun toPublicKey(bytes: ByteArray, keyAlgorithm: KeyAlgorithm): PublicKey {
            return when (keyAlgorithm) {
                KeyAlgorithm.ECDSA -> {
                    ecdsaKeyFactory.generatePublic(X509EncodedKeySpec(bytes))
                }
                KeyAlgorithm.RSA -> {
                    rsaKeyFactory.generatePublic(X509EncodedKeySpec(bytes))
                }
            }
        }

    }

    private data class KeyPair(val keyAlgo: KeyAlgorithm, val privateKey: PrivateKey, val publicKey: PublicKey)

}