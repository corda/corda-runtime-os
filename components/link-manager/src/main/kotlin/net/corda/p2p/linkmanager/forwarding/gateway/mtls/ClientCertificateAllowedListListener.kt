package net.corda.p2p.linkmanager.forwarding.gateway.mtls

import net.corda.data.p2p.mtls.AllowedCertificateSubject
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
import net.corda.schema.Schemas.P2P.Companion.P2P_MGM_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS
import java.util.concurrent.CompletableFuture

internal class ClientCertificateAllowedListListener(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    messagingConfiguration: SmartConfig,
    subscriptionFactory: SubscriptionFactory,
    private val clientCertificateSourceManager: ClientCertificateSourceManager,
) : LifecycleWithDominoTile {
    private companion object {
        const val LISTENER_NAME = "linkmanager_mtls_client_certificate_allowed_list_listener"
    }
    private val ready = CompletableFuture<Unit>()

    private val subscriptionConfig = SubscriptionConfig(
        groupName = LISTENER_NAME,
        eventTopic = P2P_MGM_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS
    )

    private val subscription = {
        subscriptionFactory.createCompactedSubscription(
            subscriptionConfig = subscriptionConfig,
            processor = Processor(),
            messagingConfig = messagingConfiguration,
        )
    }
    private inner class Processor : CompactedProcessor<String, AllowedCertificateSubject> {
        override val keyClass = String::class.java
        override val valueClass = AllowedCertificateSubject::class.java

        override fun onNext(
            newRecord: Record<String, AllowedCertificateSubject>,
            oldValue: AllowedCertificateSubject?,
            currentData: Map<String, AllowedCertificateSubject>,
        ) {
            val newValue = newRecord.value
            if (newValue != null) {
                clientCertificateSourceManager.addSource(
                    newValue.subject,
                    ClientCertificateSourceManager.MgmAllowedListSource(newValue.groupId)
                )
            } else if (oldValue != null) {
                clientCertificateSourceManager.removeSource(
                    oldValue.subject,
                    ClientCertificateSourceManager.MgmAllowedListSource(oldValue.groupId)
                )
            }
        }

        override fun onSnapshot(currentData: Map<String, AllowedCertificateSubject>) {
            ready.complete(Unit)
            currentData.values.forEach { value ->
                clientCertificateSourceManager.addSource(
                    value.subject,
                    ClientCertificateSourceManager.MgmAllowedListSource(value.groupId)
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
