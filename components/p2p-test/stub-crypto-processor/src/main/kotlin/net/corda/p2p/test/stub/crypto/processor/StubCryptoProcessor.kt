package net.corda.p2p.test.stub.crypto.processor

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.test.KeyPairEntry
import net.corda.schema.TestSchema.Companion.CRYPTO_KEYS_TOPIC
import net.corda.v5.crypto.SignatureSpec
import java.security.InvalidParameterException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class StubCryptoProcessor(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    instanceId: Int,
    configuration: SmartConfig
) : SigningCryptoService {

    private val keyPairEntryProcessor = KeyPairEntryProcessor()
    private val subscriptionConfig = SubscriptionConfig("crypto-service", CRYPTO_KEYS_TOPIC, instanceId)
    private val subscription =
        subscriptionFactory.createCompactedSubscription(subscriptionConfig, keyPairEntryProcessor, configuration)

    private val publicKeyToPrivateKey = ConcurrentHashMap<PublicKey, PrivateKey>()
    private val keyDeserialiser = KeyDeserialiser()

    private val readyFuture = CompletableFuture<Unit>()
    private val subscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        emptyList(),
        emptyList(),
    )
    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        createResources = ::createResources,
        managedChildren = listOf(subscriptionTile),
        dependentChildren = listOf(subscriptionTile),
    )

    private fun createResources(
        @Suppress("UNUSED_PARAMETER")
        resources: ResourcesHolder
    ): CompletableFuture<Unit> {
        return readyFuture
    }

    override fun sign(publicKey: PublicKey, spec: SignatureSpec, data: ByteArray): ByteArray {
        val privateKey = publicKeyToPrivateKey[publicKey] ?: throw InvalidParameterException("Could not find private key")
        val providerName = when (publicKey.algorithm) {
            "RSA" -> "SunRsaSign"
            "EC" -> "SunEC"
            else -> throw InvalidParameterException("Unsupported algorithm ${publicKey.algorithm}")
        }
        val signature = Signature.getInstance(
            spec.signatureName,
            providerName
        )
        signature.initSign(privateKey)
        signature.setParameter(spec.params)
        signature.update(data)
        return signature.sign()
    }

    private inner class KeyPairEntryProcessor : CompactedProcessor<String, KeyPairEntry> {

        override val keyClass: Class<String>
            get() = String::class.java
        override val valueClass: Class<KeyPairEntry>
            get() = KeyPairEntry::class.java

        override fun onSnapshot(currentData: Map<String, KeyPairEntry>) {
            publicKeyToPrivateKey += currentData.values.map { keyPairEntry ->
                val privateKey = keyDeserialiser.toPrivateKey(keyPairEntry.privateKey.array(), keyPairEntry.keyAlgo)
                val publicKey = keyDeserialiser.toPublicKey(keyPairEntry.publicKey.array(), keyPairEntry.keyAlgo)
                publicKey to privateKey
            }
            readyFuture.complete(Unit)
        }

        override fun onNext(
            newRecord: Record<String, KeyPairEntry>,
            oldValue: KeyPairEntry?,
            currentData: Map<String, KeyPairEntry>
        ) {
            oldValue?.let { oldKeyPairEntry ->
                val publicKey = keyDeserialiser.toPublicKey(oldKeyPairEntry.publicKey.array(), oldKeyPairEntry.keyAlgo)
                publicKeyToPrivateKey.remove(publicKey)
            }
            newRecord.value?.let { keyPairEntry ->
                val privateKey = keyDeserialiser.toPrivateKey(keyPairEntry.privateKey.array(), keyPairEntry.keyAlgo)
                val publicKey = keyDeserialiser.toPublicKey(keyPairEntry.publicKey.array(), keyPairEntry.keyAlgo)
                publicKeyToPrivateKey[publicKey] = privateKey
            }
        }
    }
}
