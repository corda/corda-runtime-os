package net.corda.p2p.linkmanager

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.ECDSA_SIGNATURE_ALGO
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.RSA_SIGNATURE_ALGO
import net.corda.p2p.schema.TestSchema.Companion.CRYPTO_KEYS_TOPIC
import net.corda.p2p.test.KeyAlgorithm
import net.corda.p2p.test.KeyPairEntry
import net.corda.v5.base.util.contextLogger
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

class StubCryptoService(lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
                        subscriptionFactory: SubscriptionFactory,
                        instanceId: Int,
                        configuration: SmartConfig): LinkManagerCryptoService {

    companion object {
        val logger = contextLogger()
    }

    private val keyPairEntryProcessor = KeyPairEntryProcessor()
    private val subscriptionConfig = SubscriptionConfig("crypto-service", CRYPTO_KEYS_TOPIC, instanceId)
    private val subscription =
        subscriptionFactory.createCompactedSubscription(subscriptionConfig, keyPairEntryProcessor, configuration)

    private val rsaSignature = Signature.getInstance(RSA_SIGNATURE_ALGO)
    private val ecdsaSignature = Signature.getInstance(ECDSA_SIGNATURE_ALGO)

    private var readyFuture = AtomicReference<CompletableFuture<Unit>>()
    override val dominoTile = DominoTile(this::class.java.simpleName, lifecycleCoordinatorFactory, ::createResources)

    private fun createResources(resources: ResourcesHolder): CompletableFuture<Unit> {
        val future = CompletableFuture<Unit>()
        readyFuture.set(future)
        subscription.start()
        resources.keep { subscription.stop() }
        return future
    }

    override fun signData(publicKey: PublicKey, data: ByteArray): ByteArray {
        return dominoTile.withLifecycleLock {
            if (!isRunning) {
                throw IllegalStateException("signData operation invoked while component was stopped.")
            }

            val (privateKey, keyAlgo) = keyPairEntryProcessor.getPrivateKey(publicKey)
                ?: throw LinkManagerCryptoService.NoPrivateKeyForGroupException(publicKey)

            return@withLifecycleLock when (keyAlgo) {
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

    private inner class KeyPairEntryProcessor: CompactedProcessor<String, KeyPairEntry> {

        private val keys = mutableMapOf<String, KeyPair>()
        private val keyDeserialiser = KeyDeserialiser()

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
                val privateKey = keyDeserialiser.toPrivateKey(keyPairEntry.privateKey.array(), keyPairEntry.keyAlgo)
                val publicKey = keyDeserialiser.toPublicKey(keyPairEntry.publicKey.array(), keyPairEntry.keyAlgo)
                keys[alias] = KeyPair(keyPairEntry.keyAlgo, privateKey, publicKey)
            }
            readyFuture.get().complete(Unit)
        }

        override fun onNext(newRecord: Record<String, KeyPairEntry>,
                            oldValue: KeyPairEntry?,
                            currentData: Map<String, KeyPairEntry>) {
            if (newRecord.value == null) {
                keys.remove(newRecord.key)
            } else {
                val keyPairEntry = newRecord.value!!
                val privateKey = keyDeserialiser.toPrivateKey(keyPairEntry.privateKey.array(), keyPairEntry.keyAlgo)
                val publicKey = keyDeserialiser.toPublicKey(keyPairEntry.publicKey.array(), keyPairEntry.keyAlgo)
                keys[newRecord.key] = KeyPair(keyPairEntry.keyAlgo, privateKey, publicKey)
            }
        }

    }

    private data class KeyPair(val keyAlgo: KeyAlgorithm, val privateKey: PrivateKey, val publicKey: PublicKey)

}
