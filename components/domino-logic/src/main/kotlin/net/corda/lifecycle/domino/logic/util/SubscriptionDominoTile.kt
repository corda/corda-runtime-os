package net.corda.lifecycle.domino.logic.util

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.NamedLifecycle
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig

@Suppress("LongParameterList")
class SubscriptionDominoTile<K, V>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionGenerator: () -> Subscription<K, V>,
    subscriptionConfig: SubscriptionConfig,
    configurationReadService: ConfigurationReadService,
    dependentChildren: Collection<LifecycleCoordinatorName>,
    managedChildren: Collection<NamedLifecycle>
) : SubscriptionDominoTileBase(
    coordinatorFactory, subscriptionGenerator, subscriptionConfig, configurationReadService, dependentChildren, managedChildren
)