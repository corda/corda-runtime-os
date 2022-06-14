package net.corda.p2p.linkmanager

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.utilities.time.Clock

@Suppress("LongParameterList")
internal class InboundLinkManager(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    commonComponents: CommonComponents,
    groups: LinkManagerGroupPolicyProvider,
    members: LinkManagerMembershipGroupReader,
    subscriptionFactory: SubscriptionFactory,
    messagingConfiguration: SmartConfig,
    clock: Clock,
) : LifecycleWithDominoTile {
    companion object {
        private const val INBOUND_MESSAGE_PROCESSOR_GROUP = "inbound_message_processor_group"
    }
    private val inboundMessageSubscription = subscriptionFactory.createEventLogSubscription(
        SubscriptionConfig(INBOUND_MESSAGE_PROCESSOR_GROUP, Schemas.P2P.LINK_IN_TOPIC),
        InboundMessageProcessor(
            commonComponents.sessionManager,
            groups,
            members,
            commonComponents.inboundAssignmentListener,
            clock
        ),
        messagingConfiguration,
        partitionAssignmentListener = commonComponents.inboundAssignmentListener
    )

    override val dominoTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        inboundMessageSubscription,
        dependentChildren = listOf(
            groups.dominoTile.coordinatorName,
            members.dominoTile.coordinatorName,
        ),
        managedChildren = listOf(commonComponents.inboundAssignmentListener.dominoTile)
    )
}
