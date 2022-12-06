package net.corda.p2p.gateway.messaging.http

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.GatewayAllowedClientCertificates
import net.corda.schema.Schemas
import net.corda.v5.base.types.MemberX500Name
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import javax.security.auth.x500.X500Principal

internal class ClientCertificatesAllowList(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    messagingConfiguration: SmartConfig,
    ): LifecycleWithDominoTile {
    private companion object {
        private const val CONSUMER_GROUP_ID = "gateway_tls_allowed_client_certificates"
    }
    private val ready = CompletableFuture<Unit>()
    private val subscriptionConfig = SubscriptionConfig(CONSUMER_GROUP_ID,
        Schemas.P2P.GATEWAY_TLS_ALLOWED_CLIENT_CERTIFICATE
    )
    private val subscription = {
        subscriptionFactory.createCompactedSubscription(
            subscriptionConfig,
            Processor(),
            messagingConfiguration
        )
    }
    private val subscriptionTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        subscriptionConfig,
        emptyList(),
        emptyList()
    )
    private val blockingDominoTile = BlockingDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        ready
    )

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        dependentChildren = listOf(subscriptionTile.coordinatorName, blockingDominoTile.coordinatorName),
        managedChildren = listOf(subscriptionTile.toNamedLifecycle(), blockingDominoTile.toNamedLifecycle()),
    )

    private val allowedClientCertificates = ConcurrentHashMap<String, MutableMap<String, Collection<MemberX500Name>>>()

    fun allowCertificates(groupIds: Collection<String>, subjects: Collection<X500Principal>) : Boolean {
        return groupIds.any { groupId ->
            println("QQQ looking at $groupId")
            allowedClientCertificates[groupId]?.values?.any { allowed ->
                println("QQQ allowed -> $allowed; subjects -> $subjects")
                subjects.asSequence()
                    .map { MemberX500Name.build(it) }
                    .any { subject ->
                        allowed.contains(subject)
                    }
            } ?: false
        }
    }


    private inner class Processor : CompactedProcessor<String, GatewayAllowedClientCertificates> {
        override val keyClass = String::class.java
        override val valueClass = GatewayAllowedClientCertificates::class.java

        override fun onSnapshot(currentData: Map<String, GatewayAllowedClientCertificates>) {
            currentData.values.forEach {
                addGroup(it)
            }
            ready.complete(Unit)
        }

        override fun onNext(
            newRecord: Record<String, GatewayAllowedClientCertificates>,
            oldValue: GatewayAllowedClientCertificates?,
            currentData: Map<String, GatewayAllowedClientCertificates>,
        ) {
            val value = newRecord.value
            if(value != null) {
                addGroup(value)
            } else {
                if(oldValue != null) {
                    allowedClientCertificates[oldValue.sourceIdentity.groupId]?.remove(oldValue.sourceIdentity.x500Name)
                }
            }
        }

        fun addGroup(record: GatewayAllowedClientCertificates) {
            println("QQQ adding group - ${record.sourceIdentity.groupId} -> ${record.allowedClientCertificates}")
            allowedClientCertificates.computeIfAbsent(record.sourceIdentity.groupId) {
                ConcurrentHashMap()
            }[record.sourceIdentity.x500Name] = record.allowedClientCertificates.map { MemberX500Name.parse(it) }
        }
    }
}
