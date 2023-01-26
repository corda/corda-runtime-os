package net.corda.p2p.linkmanager.forwarding.gateway.mtls

import net.corda.data.p2p.mtls.MemberAllowedCertificateSubject
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
import net.corda.schema.Schemas
import java.util.concurrent.CompletableFuture

internal class ClientCertificateMembersListListener(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    messagingConfiguration: SmartConfig,
    subscriptionFactory: SubscriptionFactory,
    private val clientCertificateSourceManager: ClientCertificateSourceManager,
) : LifecycleWithDominoTile {
    private companion object {
        const val LISTENER_NAME = "linkmanager_mtls_client_certificate_members_list_listener"
    }
    private val ready = CompletableFuture<Unit>()

    private val subscriptionConfig = SubscriptionConfig(
        groupName = LISTENER_NAME,
        eventTopic = Schemas.P2P.P2P_MTLS_MEMBER_CLIENT_CERTIFICATE_SUBJECT_TOPIC
    )

    private val subscription = {
        subscriptionFactory.createCompactedSubscription(
            subscriptionConfig = subscriptionConfig,
            processor = Processor(),
            messagingConfig = messagingConfiguration,
        )
    }
    private inner class Processor : CompactedProcessor<String, MemberAllowedCertificateSubject> {
        override val keyClass = String::class.java
        override val valueClass = MemberAllowedCertificateSubject::class.java

        override fun onNext(
            newRecord: Record<String, MemberAllowedCertificateSubject>,
            oldValue: MemberAllowedCertificateSubject?,
            currentData: Map<String, MemberAllowedCertificateSubject>,
        ) {
            val value = newRecord.value
            if (value != null) {
                clientCertificateSourceManager.addSource(
                    value.subject,
                    ClientCertificateSourceManager.MembershipSource(newRecord.key),
                )
            } else if (oldValue != null) {
                clientCertificateSourceManager.removeSource(
                    oldValue.subject,
                    ClientCertificateSourceManager.MembershipSource(newRecord.key),
                )
            }
        }

        override fun onSnapshot(currentData: Map<String, MemberAllowedCertificateSubject>) {
            ready.complete(Unit)
            currentData.forEach { (key, value) ->
                clientCertificateSourceManager.addSource(
                    value.subject,
                    ClientCertificateSourceManager.MembershipSource(key),
                )
            }
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
        dependentChildren = listOf(
            subscriptionDominoTile.coordinatorName,
            blockingDominoTile.coordinatorName
        ),
        managedChildren = listOf(
            subscriptionDominoTile.toNamedLifecycle(),
            blockingDominoTile.toNamedLifecycle()
        )
    )
}
