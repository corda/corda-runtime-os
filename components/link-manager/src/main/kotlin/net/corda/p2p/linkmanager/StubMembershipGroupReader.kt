package net.corda.p2p.linkmanager

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.linkmanager.LinkManagerInternalTypes.toHoldingIdentity
import net.corda.p2p.test.KeyAlgorithm
import net.corda.p2p.test.MemberInfoEntry
import net.corda.p2p.test.stub.crypto.processor.KeyDeserialiser
import net.corda.schema.TestSchema.Companion.MEMBER_INFO_TOPIC
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

internal class StubMembershipGroupReader(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    instanceId: Int,
    configuration: SmartConfig,
) : LinkManagerMembershipGroupReader {

    private val subscriptionConfig = SubscriptionConfig("member-info-reader", MEMBER_INFO_TOPIC, instanceId)
    private val messageDigest = MessageDigest.getInstance(ProtocolConstants.HASH_ALGO, BouncyCastleProvider())

    private val subscription = subscriptionFactory.createCompactedSubscription(
        subscriptionConfig,
        MembersProcessor(),
        configuration
    )

    private inner class MembersProcessor : CompactedProcessor<String, MemberInfoEntry> {
        override val keyClass = String::class.java
        override val valueClass = MemberInfoEntry::class.java

        override fun onSnapshot(currentData: Map<String, MemberInfoEntry>) {
            publicHashToMemberInformation.clear()
            membersInformation.clear()
            currentData.values.forEach {
                addMember(it)
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
                membersInformation.remove(oldValue?.holdingIdentity?.toHoldingIdentity())
                publicHashToMemberInformation.remove(oldValue?.toGroupIdWithPublicKeyHash())
            } else {
                addMember(newValue)
            }
        }

        private fun addMember(member: MemberInfoEntry) {
            membersInformation[member.holdingIdentity.toHoldingIdentity()] = member.toMemberInfo()
            publicHashToMemberInformation[member.toGroupIdWithPublicKeyHash()] = member.toMemberInfo()
        }
    }
    private val subscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        emptySet(),
        emptySet()
    )
    private val keyDeserialiser = KeyDeserialiser()

    private val readyFuture = CompletableFuture<Unit>()

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        ::createResources,
        setOf(subscriptionTile),
        setOf(subscriptionTile)
    )

    private fun createResources(@Suppress("UNUSED_PARAMETER") resources: ResourcesHolder): CompletableFuture<Unit> {
        return readyFuture
    }

    private val membersInformation = ConcurrentHashMap<LinkManagerInternalTypes.HoldingIdentity, LinkManagerInternalTypes.MemberInfo>()
    private val publicHashToMemberInformation = ConcurrentHashMap<GroupIdWithPublicKeyHash, LinkManagerInternalTypes.MemberInfo>()

    private data class GroupIdWithPublicKeyHash(
        val groupId: String,
        val hash: ByteBuffer
    )

    override fun getMemberInfo(holdingIdentity: LinkManagerInternalTypes.HoldingIdentity): LinkManagerInternalTypes.MemberInfo? {
        return membersInformation[holdingIdentity]
    }

    override fun getMemberInfo(hash: ByteArray, groupId: String): LinkManagerInternalTypes.MemberInfo? {
        return publicHashToMemberInformation[
            GroupIdWithPublicKeyHash(
                groupId,
                ByteBuffer.wrap(hash)
            )
        ]
    }

    private fun MemberInfoEntry.toMemberInfo(): LinkManagerInternalTypes.MemberInfo {
        return LinkManagerInternalTypes.MemberInfo(
            LinkManagerInternalTypes.HoldingIdentity(this.holdingIdentity.x500Name, this.holdingIdentity.groupId),
            keyDeserialiser.toPublicKey(this.publicKey.array(), this.publicKeyAlgorithm),
            this.publicKeyAlgorithm.toKeyAlgorithm(),
            LinkManagerInternalTypes.EndPoint(this.address),
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
