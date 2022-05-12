package net.corda.p2p.linkmanager

import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.schema.Schemas

internal class InboundTile(
    linkManager: LinkManager,
) : LifecycleWithDominoTile {
    private val inboundMessageSubscription = linkManager.subscriptionFactory.createEventLogSubscription(
        SubscriptionConfig(LinkManager.INBOUND_MESSAGE_PROCESSOR_GROUP, Schemas.P2P.LINK_IN_TOPIC),
        InboundMessageProcessor(
            linkManager.commonTile.sessionManager,
            linkManager.groups,
            linkManager.members,
            linkManager.commonTile.inboundAssignmentListener,
            linkManager.clock
        ),
        linkManager.configuration,
        partitionAssignmentListener = linkManager.commonTile.inboundAssignmentListener
    )

    override val dominoTile = SubscriptionDominoTile(
        linkManager.lifecycleCoordinatorFactory,
        inboundMessageSubscription,
        dependentChildren = listOf(
            linkManager.groups.dominoTile,
            linkManager.members.dominoTile,
        ),
        managedChildren = listOf(
            linkManager.commonTile.inboundAssignmentListener.dominoTile,
        )
    )
}
