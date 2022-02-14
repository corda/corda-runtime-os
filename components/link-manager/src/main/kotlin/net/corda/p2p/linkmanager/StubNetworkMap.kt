package net.corda.p2p.linkmanager

import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.NetworkType
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.test.KeyAlgorithm
import net.corda.p2p.test.NetworkMapEntry
import net.corda.schema.TestSchema.Companion.NETWORK_MAP_TOPIC
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class StubNetworkMap(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    instanceId: Int,
    configuration: SmartConfig,
) : LinkManagerNetworkMap {

    private val processor = NetworkMapEntryProcessor()
    private val subscriptionConfig = SubscriptionConfig("network-map", NETWORK_MAP_TOPIC, instanceId)
    private val subscription = subscriptionFactory.createCompactedSubscription(subscriptionConfig, processor, configuration)
    private val keyDeserialiser = KeyDeserialiser()
    private val dataForwarders = ConcurrentHashMap.newKeySet<IdentityDataForwarder>()

    private val readyFuture = AtomicReference<CompletableFuture<Unit>>()
    override val dominoTile = DominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        ::createResources,
    )

    private fun createResources(resources: ResourcesHolder): CompletableFuture<Unit> {
        val future = CompletableFuture<Unit>()
        readyFuture.set(future)
        subscription.start()
        resources.keep { subscription.stop() }
        return future
    }

    @Suppress("TYPE_INFERENCE_ONLY_INPUT_TYPES_WARNING")
    override fun getMemberInfo(holdingIdentity: LinkManagerNetworkMap.HoldingIdentity): LinkManagerNetworkMap.MemberInfo? {
        return dominoTile.withLifecycleLock {
            if (!isRunning) {
                throw IllegalStateException("getMemberInfo operation invoked while component was stopped.")
            }

            processor.netmapEntriesByHoldingIdentity[holdingIdentity]?.toMemberInfo()
        }
    }

    override fun getMemberInfo(hash: ByteArray, groupId: String): LinkManagerNetworkMap.MemberInfo? {
        return dominoTile.withLifecycleLock {
            if (!isRunning) {
                throw IllegalStateException("getMemberInfo operation invoked while component was stopped.")
            }

            processor.netMapEntriesByGroupIdPublicKeyHash[groupId]?.get(ByteBuffer.wrap(hash))?.toMemberInfo()
        }
    }

    override fun getNetworkType(groupId: String): LinkManagerNetworkMap.NetworkType? {
        return dominoTile.withLifecycleLock {
            if (!isRunning) {
                throw IllegalStateException("getNetworkType operation invoked while component was stopped.")
            }

            processor.netMapEntriesByGroupIdPublicKeyHash[groupId]
                ?.values
                ?.first()?.networkType?.toLMNetworkType()
        }
    }

    override fun getCertificates(groupId: String): List<PemCertificates>? {
        return processor.netMapEntriesByGroupIdPublicKeyHash[groupId]?.values?.first()?.trustedCertificates
    }

    override fun registerDataForwarder(forwarder: IdentityDataForwarder) {
        dataForwarders += forwarder
    }

    private fun NetworkMapEntry.toMemberInfo():LinkManagerNetworkMap.MemberInfo {
        return LinkManagerNetworkMap.MemberInfo(
            LinkManagerNetworkMap.HoldingIdentity(this.holdingIdentity.x500Name, this.holdingIdentity.groupId),
            keyDeserialiser.toPublicKey(this.publicKey.array(), this.publicKeyAlgorithm),
            this.publicKeyAlgorithm.toKeyAlgorithm(),
            LinkManagerNetworkMap.EndPoint(this.address)
        )
    }

    private fun KeyAlgorithm.toKeyAlgorithm(): net.corda.p2p.crypto.protocol.api.KeyAlgorithm {
        return when(this) {
            KeyAlgorithm.ECDSA -> net.corda.p2p.crypto.protocol.api.KeyAlgorithm.ECDSA
            KeyAlgorithm.RSA -> net.corda.p2p.crypto.protocol.api.KeyAlgorithm.RSA
        }
    }

    private fun NetworkType.toLMNetworkType(): LinkManagerNetworkMap.NetworkType {
        return when(this) {
            NetworkType.CORDA_4 -> LinkManagerNetworkMap.NetworkType.CORDA_4
            NetworkType.CORDA_5 -> LinkManagerNetworkMap.NetworkType.CORDA_5
        }
    }

    private inner class NetworkMapEntryProcessor: CompactedProcessor<String, NetworkMapEntry> {

        private val messageDigest = MessageDigest.getInstance(ProtocolConstants.HASH_ALGO, BouncyCastleProvider())

        val netMapEntriesByGroupIdPublicKeyHash = ConcurrentHashMap<String, ConcurrentHashMap<ByteBuffer, NetworkMapEntry>>()
        val netmapEntriesByHoldingIdentity = ConcurrentHashMap<LinkManagerNetworkMap.HoldingIdentity, NetworkMapEntry>()

        override val keyClass: Class<String>
            get() = String::class.java
        override val valueClass: Class<NetworkMapEntry>
            get() = NetworkMapEntry::class.java

        override fun onSnapshot(currentData: Map<String, NetworkMapEntry>) {
            currentData.forEach { (_, networkMapEntry) -> addEntry(networkMapEntry) }
            readyFuture.get().complete(Unit)
        }

        override fun onNext(
            newRecord: Record<String, NetworkMapEntry>,
            oldValue: NetworkMapEntry?,
            currentData: Map<String, NetworkMapEntry>
        ) {
            if (newRecord.value == null) {
                if (oldValue != null) {
                    val publicKeyHash = calculateHash(oldValue.publicKey.array())
                    netMapEntriesByGroupIdPublicKeyHash[oldValue.holdingIdentity.groupId]!!.remove(ByteBuffer.wrap(publicKeyHash))
                    netmapEntriesByHoldingIdentity.remove(oldValue.holdingIdentity.toLMHoldingIdentity())
                }
            } else {
                addEntry(newRecord.value!!)
            }
        }

        private fun addEntry(networkMapEntry: NetworkMapEntry) {
            if (!netMapEntriesByGroupIdPublicKeyHash.containsKey(networkMapEntry.holdingIdentity.groupId)) {
                netMapEntriesByGroupIdPublicKeyHash[networkMapEntry.holdingIdentity.groupId] = ConcurrentHashMap()
            }

            val publicKeyHash = calculateHash(networkMapEntry.publicKey.array())
            val identity = networkMapEntry.holdingIdentity.toLMHoldingIdentity()
            netMapEntriesByGroupIdPublicKeyHash[networkMapEntry.holdingIdentity.groupId]!![ByteBuffer.wrap(publicKeyHash)] = networkMapEntry
            netmapEntriesByHoldingIdentity[identity] = networkMapEntry

            dataForwarders.forEach {
                it.identityAdded(networkMapEntry.holdingIdentity.toLMHoldingIdentity())
            }
        }

        private fun HoldingIdentity.toLMHoldingIdentity(): LinkManagerNetworkMap.HoldingIdentity {
            return LinkManagerNetworkMap.HoldingIdentity(this.x500Name, this.groupId)
        }

        fun calculateHash(publicKey: ByteArray): ByteArray {
            messageDigest.reset()
            messageDigest.update(publicKey)
            return messageDigest.digest()
        }

    }
}
