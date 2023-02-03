package net.corda.p2p.linkmanager.forwarding.gateway.mtls

import net.corda.data.p2p.mtls.MemberAllowedCertificateSubject
import net.corda.data.p2p.mtls.MgmAllowedCertificateSubject
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.P2P.Companion.P2P_MGM_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS
import net.corda.schema.Schemas.P2P.Companion.P2P_MTLS_MEMBER_CLIENT_CERTIFICATE_SUBJECT_TOPIC

@Suppress("LongParameterList")
internal class ClientCertificatePublisher(
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    messagingConfiguration: SmartConfig,
    groupPolicyProvider: GroupPolicyProvider,
) : LifecycleWithDominoTile {
    private companion object {
        const val PUBLISHER_NAME = "linkmanager_mtls_client_certificate_publisher"
    }

    private val groupPolicyListener = GroupPolicyListener(
        PublisherWithDominoLogic(
            publisherFactory,
            lifecycleCoordinatorFactory,
            PublisherConfig(PUBLISHER_NAME, false),
            messagingConfiguration,
        ),
        lifecycleCoordinatorFactory,
        groupPolicyProvider,
    )

    private val clientCertificateMembersListListener = ClientCertificateBusListener.createSubscription(
        lifecycleCoordinatorFactory,
        messagingConfiguration,
        subscriptionFactory,
        P2P_MTLS_MEMBER_CLIENT_CERTIFICATE_SUBJECT_TOPIC,
        MemberAllowedCertificateSubject::getSubject,
    )

    private val clientCertificateAllowedListListener = ClientCertificateBusListener.createSubscription(
        lifecycleCoordinatorFactory,
        messagingConfiguration,
        subscriptionFactory,
        P2P_MGM_ALLOWED_CLIENT_CERTIFICATE_SUBJECTS,
        MgmAllowedCertificateSubject::getSubject,
    )

    override val dominoTile = ComplexDominoTile(
        this.javaClass.simpleName,
        lifecycleCoordinatorFactory,
        dependentChildren = listOf(
            groupPolicyListener.dominoTile.coordinatorName,
            clientCertificateMembersListListener.coordinatorName,
            clientCertificateAllowedListListener.coordinatorName,
        ),
        managedChildren = listOf(
            groupPolicyListener.dominoTile.toNamedLifecycle(),
            clientCertificateMembersListListener.toNamedLifecycle(),
            clientCertificateAllowedListListener.toNamedLifecycle(),
        )
    )
}
