package net.corda.p2p.linkmanager.inbound

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.subscription.config.SubscriptionConfig
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
    messagingConfiguration: SmartConfig,
    clock: Clock,
) : LifecycleWithDominoTile {
    companion object {
        private const val INBOUND_MESSAGE_PROCESSOR_GROUP = "inbound_message_processor_group"
    }
    private val inboundMessageSubscription = {
        subscriptionFactory.createEventLogSubscription(
            subscriptionConfig,
            InboundMessageProcessor(
                commonComponents.sessionManager,
                groupPolicyProvider,
                membershipGroupReaderProvider,
                commonComponents.inboundAssignmentListener,
                clock
            ),
            messagingConfiguration,
            partitionAssignmentListener = commonComponents.inboundAssignmentListener
        )
    }
    private val subscriptionConfig = SubscriptionConfig(INBOUND_MESSAGE_PROCESSOR_GROUP, Schemas.P2P.LINK_IN_TOPIC)

    override val dominoTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        inboundMessageSubscription,
        subscriptionConfig,
        dependentChildren = listOf(
            LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
            LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
        ),
        managedChildren = listOf(
            commonComponents.inboundAssignmentListener.dominoTile.toNamedLifecycle(),
        ),
    )
}
