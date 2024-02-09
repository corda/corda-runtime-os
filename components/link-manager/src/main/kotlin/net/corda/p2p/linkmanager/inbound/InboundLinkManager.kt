package net.corda.p2p.linkmanager.inbound

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.HttpSubscriptionDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.constants.WorkerRPCPaths
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.config.SyncRPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.linkmanager.common.CommonComponents
import net.corda.schema.Schemas
import net.corda.utilities.time.Clock

@Suppress("LongParameterList")
internal class InboundLinkManager(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    commonComponents: CommonComponents,
    groupPolicyProvider: GroupPolicyProvider,
    membershipGroupReaderProvider: MembershipGroupReaderProvider,
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    messagingConfiguration: SmartConfig,
    clock: Clock,
) : LifecycleWithDominoTile {
    companion object {
        private const val INBOUND_MESSAGE_PROCESSOR_GROUP = "inbound_message_processor_group"
        private const val INBOUND_MESSAGE_CLIENT_ID = "inbound_message_client_id"
        private const val INBOUND_MESSAGE_HTTP_SUBSCRIPTION_NAME = "inbound_message_subscription"
    }
    private val publisher = PublisherWithDominoLogic(
        publisherFactory,
        lifecycleCoordinatorFactory,
        PublisherConfig(INBOUND_MESSAGE_CLIENT_ID, false),
        messagingConfiguration,
    )
    private val processor = InboundMessageProcessor(
        commonComponents.sessionManager,
        groupPolicyProvider,
        membershipGroupReaderProvider,
        commonComponents.inboundAssignmentListener,
        clock
    )
    private val inboundMessageSubscription = {
        subscriptionFactory.createEventLogSubscription(
            subscriptionConfig,
            processor,
            messagingConfiguration,
            partitionAssignmentListener = commonComponents.inboundAssignmentListener
        )
    }
    private val subscriptionConfig = SubscriptionConfig(INBOUND_MESSAGE_PROCESSOR_GROUP, Schemas.P2P.LINK_IN_TOPIC)
    private val rpcConfig = SyncRPCConfig(
        name = INBOUND_MESSAGE_HTTP_SUBSCRIPTION_NAME,
        endpoint = WorkerRPCPaths.P2P_LINK_MANAGER_PATH,
    )
    private val rpcProcessor = InboundRpcProcessor(
        processor,
        publisher,
    )
    private val inboundHttpSubscription = {
        subscriptionFactory.createHttpRPCSubscription(
            rpcConfig,
            rpcProcessor,
        )
    }
    private val httpSubscriptionDominoTile = HttpSubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        inboundHttpSubscription,
        rpcConfig = rpcConfig,
        dependentChildren = listOf(
            LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
            LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
        ),
    )
    private val busSubscriptionDominoTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        inboundMessageSubscription,
        subscriptionConfig,
        dependentChildren = listOf(
            LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
            LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
        ),
        managedChildren = listOf()
    )
    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        lifecycleCoordinatorFactory,
        dependentChildren = setOf(
            publisher.dominoTile.coordinatorName,
            LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
            LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
        ),
        managedChildren = setOf(
            commonComponents.inboundAssignmentListener.dominoTile.toNamedLifecycle(),
            publisher.dominoTile.toNamedLifecycle(),
            busSubscriptionDominoTile.toNamedLifecycle(),
            httpSubscriptionDominoTile.toNamedLifecycle(),
        ),
        onStart = { rpcProcessor.start() },
        onClose = { rpcProcessor.stop() },
    )
}
