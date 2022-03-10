package net.corda.p2p.linkmanager

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
import net.corda.p2p.test.MemberInfoEntry
import net.corda.p2p.test.KeyAlgorithm
import net.corda.p2p.test.stub.crypto.processor.KeyDeserialiser
import net.corda.schema.TestSchema.Companion.MEMBER_INFO_TOPIC
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

internal class StubIdentitiesNetworkMap(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    instanceId: Int,
    configuration: SmartConfig,
) : LifecycleWithDominoTile {

    private val identitiesSubscriptionConfig = SubscriptionConfig("network-map", MEMBER_INFO_TOPIC, instanceId)
    private val messageDigest = MessageDigest.getInstance(ProtocolConstants.HASH_ALGO, BouncyCastleProvider())

    private val identitiesSubscription = subscriptionFactory.createCompactedSubscription(
        identitiesSubscriptionConfig,
        IdentityProcessor(),
        configuration
    )

    private inner class IdentityProcessor : CompactedProcessor<String, MemberInfoEntry> {
        override val keyClass = String::class.java
        override val valueClass = MemberInfoEntry::class.java

        override fun onSnapshot(currentData: Map<String, MemberInfoEntry>) {
            publicHashToIdentity.clear()
            identities.clear()
            currentData.values.forEach {
                addIdentity(it)
            }

            readyFuture.complete(Unit)
        }

        override fun onNext(
            newRecord: Record<String, MemberInfoEntry>,
            oldValue: MemberInfoEntry?,
            currentData: Map<String, MemberInfoEntry>,
        ) {
            val newValue = newRecord.value
            if (newValue == null) {
                identities.remove(oldValue?.holdingIdentity?.toHoldingIdentity())
                publicHashToIdentity.remove(oldValue?.toGroupIdWithPublicKeyHash())
            } else {
                addIdentity(newValue)
            }
        }

        private fun addIdentity(identity: MemberInfoEntry) {
            identities[identity.holdingIdentity.toHoldingIdentity()] = identity.toMemberInfo()
            publicHashToIdentity[identity.toGroupIdWithPublicKeyHash()] = identity.toMemberInfo()
        }
    }
    private val identitySubscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        identitiesSubscription,
        emptySet(),
        emptySet()
    )
    private val keyDeserialiser = KeyDeserialiser()

    private val readyFuture = CompletableFuture<Unit>()

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        ::createResources,
        setOf(identitySubscriptionTile),
        setOf(identitySubscriptionTile)
    )

    private fun createResources(@Suppress("UNUSED_PARAMETER") resources: ResourcesHolder): CompletableFuture<Unit> {
        return readyFuture
    }

    private val identities = ConcurrentHashMap<LinkManagerNetworkMap.HoldingIdentity, LinkManagerNetworkMap.MemberInfo>()
    private val publicHashToIdentity = ConcurrentHashMap<GroupIdWithPublicKeyHash, LinkManagerNetworkMap.MemberInfo>()

    private data class GroupIdWithPublicKeyHash(
        val groupId: String,
        val hash: ByteBuffer
    )

    fun getMemberInfo(holdingIdentity: LinkManagerNetworkMap.HoldingIdentity): LinkManagerNetworkMap.MemberInfo? {
        return identities[holdingIdentity]
    }

    fun getMemberInfo(hash: ByteArray, groupId: String): LinkManagerNetworkMap.MemberInfo? {
        return publicHashToIdentity[
            GroupIdWithPublicKeyHash(
                groupId,
                ByteBuffer.wrap(hash)
            )
        ]
    }

    private fun MemberInfoEntry.toMemberInfo(): LinkManagerNetworkMap.MemberInfo {
        return LinkManagerNetworkMap.MemberInfo(
            LinkManagerNetworkMap.HoldingIdentity(this.holdingIdentity.x500Name, this.holdingIdentity.groupId),
            keyDeserialiser.toPublicKey(this.publicKey.array(), this.publicKeyAlgorithm),
            this.publicKeyAlgorithm.toKeyAlgorithm(),
            LinkManagerNetworkMap.EndPoint(this.address),
        )
    }

    private fun KeyAlgorithm.toKeyAlgorithm(): net.corda.p2p.crypto.protocol.api.KeyAlgorithm {
        return when (this) {
            KeyAlgorithm.ECDSA -> net.corda.p2p.crypto.protocol.api.KeyAlgorithm.ECDSA
            KeyAlgorithm.RSA -> net.corda.p2p.crypto.protocol.api.KeyAlgorithm.RSA
        }
    }
    private fun calculateHash(publicKey: ByteArray): ByteArray {
        messageDigest.reset()
        messageDigest.update(publicKey)
        return messageDigest.digest()
    }
    private fun MemberInfoEntry.toGroupIdWithPublicKeyHash(): GroupIdWithPublicKeyHash {
        return GroupIdWithPublicKeyHash(
            this.holdingIdentity.groupId,
            ByteBuffer.wrap(
                calculateHash(
                    this.publicKey.array()
                )
            )
        )
    }
}
