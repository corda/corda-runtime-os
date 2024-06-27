package net.corda.lifecycle.domino.logic.util

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.NamedLifecycle
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.config.SyncRPCConfig

@Suppress("LongParameterList")
class HttpSubscriptionDominoTile<REQUEST, RESPONSE>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionGenerator: () -> RPCSubscription<REQUEST, RESPONSE>,
    rpcConfig: SyncRPCConfig,
    configurationReadService: ConfigurationReadService,
    dependentChildren: Collection<LifecycleCoordinatorName>,
    managedChildren: Collection<NamedLifecycle> = emptyList(),
) : SubscriptionDominoTileBase(
    coordinatorFactory,
    subscriptionGenerator,
    SubscriptionConfig(rpcConfig.name, rpcConfig.endpoint),
    configurationReadService,
    dependentChildren,
    managedChildren,
)
