package net.corda.lifecycle.domino.logic.util

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.NamedLifecycle
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig

class RPCSubscriptionDominoTile<REQUEST, RESPONSE>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionGenerator: () -> RPCSubscription<REQUEST, RESPONSE>,
    rpcConfig: RPCConfig<REQUEST, RESPONSE>,
    dependentChildren: Collection<LifecycleCoordinatorName>,
    managedChildren: Collection<NamedLifecycle>
): SubscriptionDominoTileBase(
    coordinatorFactory,
    subscriptionGenerator,
    SubscriptionConfig(rpcConfig.groupName, rpcConfig.clientName),
    dependentChildren,
    managedChildren
)
