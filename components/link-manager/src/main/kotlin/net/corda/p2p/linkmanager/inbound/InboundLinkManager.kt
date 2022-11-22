package net.corda.p2p.linkmanager.inbound

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.linkmanager.common.CommonComponents
import net.corda.p2p.linkmanager.grouppolicy.LinkManagerGroupPolicyProvider
import net.corda.p2p.linkmanager.membership.LinkManagerMembershipGroupReader
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
    private val inboundMessageSubscription = {
        subscriptionFactory.createEventLogSubscription(
            subscriptionConfig,
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
    }
    private val subscriptionConfig = SubscriptionConfig(INBOUND_MESSAGE_PROCESSOR_GROUP, Schemas.P2P.LINK_IN_TOPIC)

    override val dominoTile = SubscriptionDominoTile(
        lifecycleCoordinatorFactory,
        inboundMessageSubscription,
        subscriptionConfig,
        dependentChildren = listOf(
            groups.dominoTile.coordinatorName,
            members.dominoTile.coordinatorName,
        ),
        managedChildren = listOf(
            commonComponents.inboundAssignmentListener.dominoTile.toNamedLifecycle(),
        ),
    )
}
