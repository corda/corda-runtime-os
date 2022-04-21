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
import net.corda.p2p.linkmanager.LinkManagerInternalTypes.toHoldingIdentity
import net.corda.p2p.linkmanager.PublicKeyReader.Companion.toKeyAlgorithm
import net.corda.p2p.test.MemberInfoEntry
import net.corda.schema.TestSchema.Companion.MEMBER_INFO_TOPIC
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

internal class StubMembershipGroupReader(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    configuration: SmartConfig,
) : LinkManagerMembershipGroupReader {

    private val subscriptionConfig = SubscriptionConfig("member-info-reader", MEMBER_INFO_TOPIC)

    private val publicKeyReader = PublicKeyReader()
    private val keyHasher = KeyHasher()

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
            if (oldValue != null) {
                publicHashToMemberInformation.remove(oldValue.toGroupIdWithPublicKeyHash())
                membersInformation.remove(oldValue.holdingIdentity.toHoldingIdentity())
            }
            if (newValue != null) {
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
    private val publicHashToMemberInformation =
        ConcurrentHashMap<GroupIdWithPublicKeyHash, LinkManagerInternalTypes.MemberInfo>()

    override fun getMemberInfo(holdingIdentity: LinkManagerInternalTypes.HoldingIdentity) = membersInformation[holdingIdentity]

    override fun getMemberInfo(hash: ByteArray, groupId: String) =
        publicHashToMemberInformation[
            GroupIdWithPublicKeyHash(
                groupId,
                ByteBuffer.wrap(hash)
            )
        ]

    private fun MemberInfoEntry.toMemberInfo(): LinkManagerInternalTypes.MemberInfo {
        val publicKey = publicKeyReader.loadPublicKey(this.sessionPublicKey)
        return LinkManagerInternalTypes.MemberInfo(
            LinkManagerInternalTypes.HoldingIdentity(this.holdingIdentity.x500Name, this.holdingIdentity.groupId),
            publicKey,
            publicKey.toKeyAlgorithm(),
            LinkManagerInternalTypes.EndPoint(this.address),
        )
    }

    private fun MemberInfoEntry.toGroupIdWithPublicKeyHash(): GroupIdWithPublicKeyHash {
        val publicKey = publicKeyReader.loadPublicKey(this.sessionPublicKey)
        return GroupIdWithPublicKeyHash(
            this.holdingIdentity.groupId,
            ByteBuffer.wrap(keyHasher.hash(publicKey)),
        )
    }
}
