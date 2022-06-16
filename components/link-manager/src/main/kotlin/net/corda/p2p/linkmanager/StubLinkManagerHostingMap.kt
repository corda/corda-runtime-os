package net.corda.p2p.linkmanager

import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.test.HostedIdentityEntry
import net.corda.schema.Schemas.P2P.Companion.P2P_HOSTED_IDENTITIES_TOPIC
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

internal class StubLinkManagerHostingMap(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    configuration: SmartConfig,
) : LinkManagerHostingMap {
    companion object {
        private const val GROUP_NAME = "linkmanager_stub_hosting_map"
    }

    private val locallyHostedIdentityToIdentityInfo =
        ConcurrentHashMap<HoldingIdentity, HostingMapListener.IdentityInfo>()
    private val publicHashToIdentityInfo =
        ConcurrentHashMap<GroupIdWithPublicKeyHash, HostingMapListener.IdentityInfo>()
    private val publicKeyReader = PublicKeyReader()
    private val keyHasher = KeyHasher()
    private val listeners = ConcurrentHashMap.newKeySet<HostingMapListener>()
    private val ready = CompletableFuture<Unit>()
    private val subscription = subscriptionFactory.createCompactedSubscription(
        SubscriptionConfig(
            GROUP_NAME,
            P2P_HOSTED_IDENTITIES_TOPIC,
        ),
        Processor(),
        configuration,
    )

    private val subscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        emptyList(),
        emptyList(),
    )

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        ::createResources,
        setOf(subscriptionTile),
        setOf(subscriptionTile)
    )

    private fun createResources(
        @Suppress("UNUSED_PARAMETER")
        resourcesHolder: ResourcesHolder
    ): CompletableFuture<Unit> {
        return ready
    }

    override fun isHostedLocally(identity: HoldingIdentity) =
        locallyHostedIdentityToIdentityInfo.containsKey(identity)

    override fun getInfo(identity: HoldingIdentity) =
        locallyHostedIdentityToIdentityInfo[identity]

    override fun getInfo(hash: ByteArray, groupId: String) = publicHashToIdentityInfo[
        GroupIdWithPublicKeyHash(
            groupId,
            ByteBuffer.wrap(hash)
        )
    ]

    override fun registerListener(listener: HostingMapListener) {
        listeners.add(listener)
    }

    private inner class Processor : CompactedProcessor<String, HostedIdentityEntry> {
        override val keyClass = String::class.java
        override val valueClass = HostedIdentityEntry::class.java
        override fun onSnapshot(currentData: Map<String, HostedIdentityEntry>) {
            locallyHostedIdentityToIdentityInfo.clear()
            publicHashToIdentityInfo.clear()
            currentData.values.forEach {
                addEntry(it)
            }
            ready.complete(Unit)
        }

        override fun onNext(
            newRecord: Record<String, HostedIdentityEntry>,
            oldValue: HostedIdentityEntry?,
            currentData: Map<String, HostedIdentityEntry>,
        ) {
            if (oldValue != null) {
                publicHashToIdentityInfo.remove(oldValue.toGroupIdWithPublicKeyHash())
                locallyHostedIdentityToIdentityInfo.remove(oldValue.holdingIdentity)
            }
            val newIdentity = newRecord.value
            if (newIdentity != null) {
                addEntry(newIdentity)
            }
        }
    }

    private fun HostedIdentityEntry.toGroupIdWithPublicKeyHash(): GroupIdWithPublicKeyHash {
        val publicKey = publicKeyReader.loadPublicKey(this.sessionPublicKey)
        return GroupIdWithPublicKeyHash(
            this.holdingIdentity.groupId,
            ByteBuffer.wrap(keyHasher.hash(publicKey)),
        )
    }

    private fun addEntry(entry: HostedIdentityEntry) {
        val info = HostingMapListener.IdentityInfo(
            holdingIdentity = entry.holdingIdentity,
            tlsCertificates = entry.tlsCertificates,
            tlsTenantId = entry.tlsTenantId,
            sessionKeyTenantId = entry.sessionKeyTenantId,
            sessionPublicKey = publicKeyReader.loadPublicKey(entry.sessionPublicKey)
        )
        locallyHostedIdentityToIdentityInfo[entry.holdingIdentity] = info
        publicHashToIdentityInfo[entry.toGroupIdWithPublicKeyHash()] = info
        listeners.forEach {
            it.identityAdded(info)
        }
    }
}
