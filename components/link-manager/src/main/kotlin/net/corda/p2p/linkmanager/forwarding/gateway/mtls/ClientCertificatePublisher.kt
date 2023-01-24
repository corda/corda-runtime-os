package net.corda.p2p.linkmanager.forwarding.gateway.mtls

import net.corda.data.p2p.mtls.AllClientCertificateSubjects
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.P2P.Companion.P2P_ALL_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS
import net.corda.virtualnode.HoldingIdentity
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

internal class ClientCertificatePublisher(
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    messagingConfiguration: SmartConfig,
) : LifecycleWithDominoTile, ClientCertificateSourceManager.Listener {
    private companion object {
        const val PUBLISHER_NAME = "linkmanager_mtls_client_certificate_publisher_publisher"
        const val LISTENER_NAME = "linkmanager_mtls_client_certificate_current_list_listener"
    }

    private val ready = CompletableFuture<Unit>()

    private val publishedCertificates = ConcurrentHashMap.newKeySet<String>()
    private val toPublish = ConcurrentLinkedQueue<Record<String, AllClientCertificateSubjects>>()
    private val holdingIdentityToCertificate = ConcurrentHashMap<HoldingIdentity, String>()
    private val knownCertificates = ClientCertificateSourceManager(this)

    private val clientCertificateMembersListListener = ClientCertificateMembersListListener(
        lifecycleCoordinatorFactory,
        messagingConfiguration,
        subscriptionFactory,
        knownCertificates,
    )
    private val clientCertificateAllowedListListener = ClientCertificateAllowedListListener(
        lifecycleCoordinatorFactory,
        messagingConfiguration,
        subscriptionFactory,
        knownCertificates,
    )

    fun groupAdded(holdingIdentity: HoldingIdentity, groupPolicy: GroupPolicy) {
        val mgmClientCertificateSubject = groupPolicy.p2pParameters.mgmClientCertificateSubject?.toString()
        if (mgmClientCertificateSubject != null) {
            val oldCertificateSubject = holdingIdentityToCertificate.put(holdingIdentity, mgmClientCertificateSubject)
            knownCertificates.addSource(
                mgmClientCertificateSubject,
                ClientCertificateSourceManager.GroupPolicySource(holdingIdentity),
            )
            if (oldCertificateSubject != null) {
                knownCertificates.removeSource(
                    oldCertificateSubject,
                    ClientCertificateSourceManager.GroupPolicySource(holdingIdentity),
                )
            }
        } else {
            val oldSubject = holdingIdentityToCertificate.remove(holdingIdentity)
            if (oldSubject != null) {
                knownCertificates.removeSource(
                    oldSubject,
                    ClientCertificateSourceManager.GroupPolicySource(holdingIdentity),
                )
            }
        }
    }

    private fun publishQueueIfPossible() {
        while ((publisher.isRunning) && (ready.isDone)) {
            val record = toPublish.poll() ?: return
            publisher.publish(listOf(record))
        }
    }

    private val publisher = PublisherWithDominoLogic(
        publisherFactory,
        lifecycleCoordinatorFactory,
        PublisherConfig(PUBLISHER_NAME, false),
        messagingConfiguration,
    )

    private val subscriptionConfig = SubscriptionConfig(
        groupName = LISTENER_NAME,
        eventTopic = P2P_ALL_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS
    )
    private val subscription = {
        subscriptionFactory.createCompactedSubscription(
            subscriptionConfig = subscriptionConfig,
            processor = Processor(),
            messagingConfig = messagingConfiguration,
        )
    }
    private inner class Processor : CompactedProcessor<String, AllClientCertificateSubjects> {
        override val keyClass = String::class.java
        override val valueClass = AllClientCertificateSubjects::class.java

        override fun onNext(
            newRecord: Record<String, AllClientCertificateSubjects>,
            oldValue: AllClientCertificateSubjects?,
            currentData: Map<String, AllClientCertificateSubjects>,
        ) {
            val value = newRecord.value
            if (value != null) {
                publishedCertificates.add(value.subject)
            } else if (oldValue != null) {
                publishedCertificates.remove(oldValue.subject)
            }
        }

        override fun onSnapshot(currentData: Map<String, AllClientCertificateSubjects>) {
            publishedCertificates.addAll(
                currentData.values.map { it.subject }
            )
            ready.complete(Unit)
        }
    }

    private val subscriptionDominoTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        subscription,
        subscriptionConfig,
        emptyList(),
        emptyList(),
    )
    private val blockingDominoTile = BlockingDominoTile(
        this.javaClass.simpleName,
        lifecycleCoordinatorFactory,
        ready
    )

    override val dominoTile = ComplexDominoTile(
        this.javaClass.simpleName,
        lifecycleCoordinatorFactory,
        onStart = ::publishQueueIfPossible,
        dependentChildren = listOf(
            subscriptionDominoTile.coordinatorName,
            blockingDominoTile.coordinatorName,
            publisher.dominoTile.coordinatorName,
            clientCertificateMembersListListener.dominoTile.coordinatorName,
            clientCertificateAllowedListListener.dominoTile.coordinatorName,
        ),
        managedChildren = listOf(
            subscriptionDominoTile.toNamedLifecycle(),
            blockingDominoTile.toNamedLifecycle(),
            publisher.dominoTile.toNamedLifecycle(),
            clientCertificateMembersListListener.dominoTile.toNamedLifecycle(),
            clientCertificateAllowedListListener.dominoTile.toNamedLifecycle(),
        )
    )

    override fun publishSubject(subject: String) {
        if (publishedCertificates.add(subject)) {
            toPublish.offer(
                Record(
                    P2P_ALL_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS,
                    subject,
                    AllClientCertificateSubjects(subject),
                )
            )
            publishQueueIfPossible()
        }
    }

    override fun removeSubject(subject: String) {
        if (publishedCertificates.remove(subject)) {
            toPublish.offer(
                Record(
                    P2P_ALL_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS,
                    subject,
                    null,
                )
            )
        }
        publishQueueIfPossible()
    }
}
