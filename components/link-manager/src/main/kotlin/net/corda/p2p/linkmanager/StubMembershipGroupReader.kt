package net.corda.p2p.linkmanager

import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.lifecycle.registry.LifecycleRegistry
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.linkmanager.PublicKeyReader.Companion.toKeyAlgorithm
import net.corda.p2p.test.MemberInfoEntry
import net.corda.schema.TestSchema.Companion.MEMBER_INFO_TOPIC
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

internal class StubMembershipGroupReader(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    registry: LifecycleRegistry,
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
                membersInformation.remove(oldValue.holdingIdentity)
            }
            if (newValue != null) {
                addMember(newValue)
            }
        }

        private fun addMember(member: MemberInfoEntry) {
            membersInformation[member.holdingIdentity] = member.toMemberInfo()
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
    private val blockingTile = BlockingDominoTile(this::class.java.simpleName, lifecycleCoordinatorFactory, readyFuture)

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        registry,
        ::onStart,
        dependentChildren = setOf(subscriptionTile.coordinatorName, blockingTile.coordinatorName),
        managedChildren = setOf(subscriptionTile, blockingTile)
    )

    private fun onStart(): CompletableFuture<Unit> {
        return readyFuture
    }

    private val membersInformation = ConcurrentHashMap<HoldingIdentity, LinkManagerMembershipGroupReader.MemberInfo>()
    private val publicHashToMemberInformation =
        ConcurrentHashMap<GroupIdWithPublicKeyHash, LinkManagerMembershipGroupReader.MemberInfo>()

    override fun getMemberInfo(holdingIdentity: HoldingIdentity) = membersInformation[holdingIdentity]

    override fun getMemberInfo(hash: ByteArray, groupId: String) =
        publicHashToMemberInformation[
            GroupIdWithPublicKeyHash(
                groupId,
                ByteBuffer.wrap(hash)
            )
        ]

    private fun MemberInfoEntry.toMemberInfo(): LinkManagerMembershipGroupReader.MemberInfo {
        val publicKey = publicKeyReader.loadPublicKey(this.sessionPublicKey)
        return LinkManagerMembershipGroupReader.MemberInfo(
            HoldingIdentity(this.holdingIdentity.x500Name, this.holdingIdentity.groupId),
            publicKey,
            publicKey.toKeyAlgorithm(),
            this.address,
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
