package net.corda.p2p.linkmanager.membership

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.linkmanager.common.PublicKeyReader.Companion.toKeyAlgorithm
import net.corda.p2p.linkmanager.common.GroupIdWithPublicKeyHash
import net.corda.p2p.linkmanager.common.KeyHasher
import net.corda.p2p.linkmanager.common.PublicKeyReader
import net.corda.data.p2p.test.MemberInfoEntry
import net.corda.schema.Schemas.P2P.Companion.MEMBER_INFO_TOPIC
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
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

    private val subscription = {
        subscriptionFactory.createCompactedSubscription(
            subscriptionConfig,
            MembersProcessor(),
            configuration
        )
    }

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
                membersInformation.remove(oldValue.holdingIdentity.toCorda())
            }
            if (newValue != null) {
                addMember(newValue)
            }
        }

        private fun addMember(member: MemberInfoEntry) {
            membersInformation[member.holdingIdentity.toCorda()] = member.toMemberInfo()
            publicHashToMemberInformation[member.toGroupIdWithPublicKeyHash()] = member.toMemberInfo()
        }
    }
    private val subscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        subscriptionConfig,
        emptySet(),
        emptySet()
    )
    private val readyFuture = CompletableFuture<Unit>()
    private val blockingTile = BlockingDominoTile(this::class.java.simpleName, lifecycleCoordinatorFactory, readyFuture)

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        dependentChildren = setOf(subscriptionTile.coordinatorName, blockingTile.coordinatorName),
        managedChildren = setOf(subscriptionTile.toNamedLifecycle(), blockingTile.toNamedLifecycle())
    )

    private val membersInformation = ConcurrentHashMap<HoldingIdentity, LinkManagerMembershipGroupReader.MemberInfo>()
    private val publicHashToMemberInformation =
        ConcurrentHashMap<GroupIdWithPublicKeyHash, LinkManagerMembershipGroupReader.MemberInfo>()

    //All Members have the same view of the members map in the StubMembershipGroupReader, so we ignore the requestingIdentity.
    override fun getMemberInfo(requestingIdentity: HoldingIdentity, lookupIdentity: HoldingIdentity) =
        membersInformation[lookupIdentity]

    override fun getMemberInfo(requestingIdentity: HoldingIdentity, publicKeyHashToLookup: ByteArray) =
        publicHashToMemberInformation[GroupIdWithPublicKeyHash(requestingIdentity.groupId, ByteBuffer.wrap(publicKeyHashToLookup))]

    private fun MemberInfoEntry.toMemberInfo(): LinkManagerMembershipGroupReader.MemberInfo {
        val publicKey = publicKeyReader.loadPublicKey(this.sessionPublicKey)
        return LinkManagerMembershipGroupReader.MemberInfo(
            this.holdingIdentity.toCorda(),
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
