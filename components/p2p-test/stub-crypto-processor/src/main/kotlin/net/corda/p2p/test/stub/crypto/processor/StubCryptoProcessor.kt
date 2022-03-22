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
import net.corda.p2p.test.TenantKeys
import net.corda.schema.TestSchema.Companion.CRYPTO_KEYS_TOPIC
import net.corda.v5.crypto.SignatureSpec
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class StubCryptoProcessor(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    configuration: SmartConfig
) : CryptoProcessor {

    private val keyPairEntryProcessor = KeyPairEntryProcessor()
    private val subscriptionConfig = SubscriptionConfig("crypto-service", CRYPTO_KEYS_TOPIC)
    private val subscription =
        subscriptionFactory.createCompactedSubscription(subscriptionConfig, keyPairEntryProcessor, configuration)
    private class TenantKeyMap {
        val publicKeyToPrivateKey = ConcurrentHashMap<PublicKey, PrivateKey>()
    }

    private val tenantIdToKeys = ConcurrentHashMap<String, TenantKeyMap>()
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

    override fun sign(
        tenantId: String,
        publicKey: PublicKey,
        spec: SignatureSpec,
        data: ByteArray
    ): ByteArray {
        val privateKey = tenantIdToKeys[tenantId]
            ?.publicKeyToPrivateKey
            ?.get(publicKey)
            ?: throw CouldNotFindPrivateKey()
        val providerName = when (publicKey.algorithm) {
            "RSA" -> "SunRsaSign"
            "EC" -> "SunEC"
            else -> throw UnsupportedAlgorithm(publicKey)
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

    private inner class KeyPairEntryProcessor : CompactedProcessor<String, TenantKeys> {

        override val keyClass = String::class.java
        override val valueClass = TenantKeys::class.java

        override fun onSnapshot(currentData: Map<String, TenantKeys>) {
            currentData.values.forEach { tenantKeys ->
                addItem(tenantKeys)
            }
            readyFuture.complete(Unit)
        }

        override fun onNext(
            newRecord: Record<String, TenantKeys>,
            oldValue: TenantKeys?,
            currentData: Map<String, TenantKeys>
        ) {
            oldValue?.let { oldTenantKeys ->
                val tenantKeyMap = tenantIdToKeys[oldTenantKeys.tenantId]
                if (tenantKeyMap != null) {
                    val publicKey = keyDeserialiser.toPublicKey(oldTenantKeys.keys.publicKey.array(), oldTenantKeys.keys.keyAlgo)
                    tenantKeyMap.publicKeyToPrivateKey.remove(publicKey)
                }
            }
            newRecord.value?.let { tenantKeys ->
                addItem(tenantKeys)
            }
        }

        private fun addItem(tenantKeys: TenantKeys) {
            val privateKey = keyDeserialiser.toPrivateKey(
                tenantKeys.keys.privateKey.array(),
                tenantKeys.keys.keyAlgo
            )
            val publicKey = keyDeserialiser.toPublicKey(
                tenantKeys.keys.publicKey.array(),
                tenantKeys.keys.keyAlgo
            )
            val tenantKeyMap = tenantIdToKeys.computeIfAbsent(tenantKeys.tenantId) {
                TenantKeyMap()
            }
            tenantKeyMap.publicKeyToPrivateKey[publicKey] = privateKey
        }
    }
}
