package net.corda.p2p.linkmanager.hosting

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.p2p.linkmanager.common.GroupIdWithPublicKeyHash
import net.corda.p2p.linkmanager.common.KeyHasher
import net.corda.p2p.linkmanager.common.PublicKeyReader
import net.corda.schema.Schemas.P2P.Companion.P2P_HOSTED_IDENTITIES_TOPIC
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

internal class LinkManagerHostingMapImpl(
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
    private val subscriptionConfig = SubscriptionConfig(
        GROUP_NAME,
        P2P_HOSTED_IDENTITIES_TOPIC,
    )
    private val subscription = {
        subscriptionFactory.createCompactedSubscription(
            subscriptionConfig,
            Processor(),
            configuration,
        )
    }

    private val subscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        subscriptionConfig,
        emptyList(),
        emptyList(),
    )
    private val blockingTile = BlockingDominoTile(this::class.java.simpleName, lifecycleCoordinatorFactory, ready)

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        dependentChildren = setOf(subscriptionTile.coordinatorName, blockingTile.coordinatorName),
        managedChildren = setOf(subscriptionTile.toNamedLifecycle(), blockingTile.toNamedLifecycle())
    )


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

    override fun allLocallyHostedIdentities(): List<HoldingIdentity> {
        return locallyHostedIdentityToIdentityInfo.keys().toList()
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
                locallyHostedIdentityToIdentityInfo.remove(oldValue.holdingIdentity.toCorda())
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
            holdingIdentity = entry.holdingIdentity.toCorda(),
            tlsCertificates = entry.tlsCertificates,
            tlsTenantId = entry.tlsTenantId,
            sessionKeyTenantId = entry.sessionKeyTenantId,
            sessionPublicKey = publicKeyReader.loadPublicKey(entry.sessionPublicKey),
            sessionCertificates = entry.sessionCertificates
        )
        locallyHostedIdentityToIdentityInfo[entry.holdingIdentity.toCorda()] = info
        publicHashToIdentityInfo[entry.toGroupIdWithPublicKeyHash()] = info
        listeners.forEach {
            it.identityAdded(info)
        }
    }
}
