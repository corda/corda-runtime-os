package net.corda.p2p.test.stub.crypto.processor

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.data.p2p.test.TenantKeys
import net.corda.schema.Schemas.P2P.Companion.CRYPTO_KEYS_TOPIC
import net.corda.v5.crypto.ParameterizedSignatureSpec
import net.corda.v5.crypto.SignatureSpec
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class StubCryptoProcessor(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    messagingConfiguration: SmartConfig
) : CryptoProcessor {

    private val keyPairEntryProcessor = KeyPairEntryProcessor()
    private val subscriptionConfig = SubscriptionConfig("crypto-service", CRYPTO_KEYS_TOPIC)
    private val subscription = {
        subscriptionFactory.createCompactedSubscription(
            subscriptionConfig,
            keyPairEntryProcessor,
            messagingConfiguration
        )
    }
    private class TenantKeyMap {
        val publicKeyToPrivateKey = ConcurrentHashMap<PublicKey, PrivateKey>()
    }

    private val tenantIdToKeys = ConcurrentHashMap<String, TenantKeyMap>()
    private val keyDeserialiser = KeyDeserialiser()

    private val readyFuture = CompletableFuture<Unit>()
    private val subscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        subscriptionConfig,
        emptyList(),
        emptyList(),
    )
    private val blockingDominoTile = BlockingDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        readyFuture
    )
    override val namedLifecycle = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        dependentChildren = listOf(subscriptionTile.coordinatorName, blockingDominoTile.coordinatorName),
        managedChildren = listOf(subscriptionTile.toNamedLifecycle(), blockingDominoTile.toNamedLifecycle()),
    ).toNamedLifecycle()

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
        (spec as? ParameterizedSignatureSpec)?.let { signature.setParameter(it.params) }
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
                    val publicKey = keyDeserialiser.toKeyPair(oldTenantKeys.keys.keyPair).public
                    tenantKeyMap.publicKeyToPrivateKey.remove(publicKey)
                }
            }
            newRecord.value?.let { tenantKeys ->
                addItem(tenantKeys)
            }
        }

        private fun addItem(tenantKeys: TenantKeys) {
            val pair = keyDeserialiser.toKeyPair(tenantKeys.keys.keyPair)
            val tenantKeyMap = tenantIdToKeys.computeIfAbsent(tenantKeys.tenantId) {
                TenantKeyMap()
            }
            tenantKeyMap.publicKeyToPrivateKey[pair.public] = pair.private
        }
    }
}
