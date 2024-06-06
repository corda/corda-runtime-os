package net.corda.lifecycle.domino.logic.util

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.NamedLifecycle
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.config.SubscriptionConfig

@Suppress("LongParameterList")
class RPCSubscriptionDominoTile<REQUEST, RESPONSE>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionGenerator: () -> RPCSubscription<REQUEST, RESPONSE>,
    rpcConfig: RPCConfig<REQUEST, RESPONSE>,
    configurationReadService: ConfigurationReadService,
    configKey: String,
    dependentChildren: Collection<LifecycleCoordinatorName>,
    managedChildren: Collection<NamedLifecycle>
) : SubscriptionDominoTileBase(
    coordinatorFactory,
    subscriptionGenerator,
    SubscriptionConfig(rpcConfig.groupName, rpcConfig.clientName),
    configurationReadService,
    configKey,
    dependentChildren,
    managedChildren
)
