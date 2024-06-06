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
import net.corda.data.p2p.HostedIdentitySessionKeyAndCert
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.MemberInfoExtension.Companion.sessionInitiationKeys
import net.corda.p2p.linkmanager.common.GroupIdWithPublicKeyHash
import net.corda.p2p.linkmanager.common.KeyHasher
import net.corda.p2p.linkmanager.common.PublicKeyReader
import net.corda.schema.Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import net.corda.configuration.read.ConfigurationReadService
import net.corda.schema.configuration.ConfigKeys.P2P_LINK_MANAGER_CONFIG

internal class LinkManagerHostingMapImpl(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    configuration: SmartConfig,
    configurationReadService: ConfigurationReadService,
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
        configurationReadService = configurationReadService,
        configKey = P2P_LINK_MANAGER_CONFIG,
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

    override fun isHostedLocallyAndSessionKeyMatch(member: MemberInfo) =
        locallyHostedIdentityToIdentityInfo[member.holdingIdentity]?.let { identityInfo ->
            member.sessionInitiationKeys.any { memberSessionKey ->
                identityInfo.allSessionKeysAndCertificates.any {
                    it.sessionPublicKey.encoded.contentEquals(memberSessionKey.encoded)
                }
            }
        } ?: false

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
                oldValue.toGroupIdWithPublicKeyHash().forEach {
                    publicHashToIdentityInfo.remove(it)
                }
                locallyHostedIdentityToIdentityInfo.remove(oldValue.holdingIdentity.toCorda())
            }
            val newIdentity = newRecord.value
            if (newIdentity != null) {
                addEntry(newIdentity)
            }
        }
    }

    private fun HostedIdentityEntry.toGroupIdWithPublicKeyHash(): Collection<GroupIdWithPublicKeyHash> {
        return this.alternativeSessionKeysAndCerts.map {
            it.toGroupIdWithPublicKeyHash(this.holdingIdentity.groupId)
        } + this.preferredSessionKeyAndCert.toGroupIdWithPublicKeyHash(this.holdingIdentity.groupId)
    }

    private fun addEntry(entry: HostedIdentityEntry) {
        val preferredSessionKey = entry.preferredSessionKeyAndCert.toCorda()
        val info = HostingMapListener.IdentityInfo(
            holdingIdentity = entry.holdingIdentity.toCorda(),
            tlsCertificates = entry.tlsCertificates,
            tlsTenantId = entry.tlsTenantId,
            preferredSessionKeyAndCertificates = preferredSessionKey,
            alternativeSessionKeysAndCertificates = entry.alternativeSessionKeysAndCerts.map { it.toCorda() }
        )
        locallyHostedIdentityToIdentityInfo[entry.holdingIdentity.toCorda()] = info
        entry.toGroupIdWithPublicKeyHash().forEach {
            publicHashToIdentityInfo[it] = info
        }
        listeners.forEach {
            it.identityAdded(info)
        }
    }

    private fun HostedIdentitySessionKeyAndCert.toCorda(): HostingMapListener.SessionKeyAndCertificates {
        return HostingMapListener.SessionKeyAndCertificates(
            sessionPublicKey = publicKeyReader.loadPublicKey(this.sessionPublicKey),
            sessionCertificateChain = this.sessionCertificates,
        )
    }

    private fun HostedIdentitySessionKeyAndCert.toGroupIdWithPublicKeyHash(groupId: String): GroupIdWithPublicKeyHash {
        val publicKey = publicKeyReader.loadPublicKey(this.sessionPublicKey)
        return GroupIdWithPublicKeyHash(
            groupId,
            ByteBuffer.wrap(keyHasher.hash(publicKey)),
        )
    }
}
