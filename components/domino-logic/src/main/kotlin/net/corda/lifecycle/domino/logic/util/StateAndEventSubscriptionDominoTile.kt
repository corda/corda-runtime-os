package net.corda.lifecycle.domino.logic.util

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.NamedLifecycle
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig

class StateAndEventSubscriptionDominoTile<K, S, E>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionGenerator: () -> StateAndEventSubscription<K, S, E>,
    subscriptionConfig: SubscriptionConfig,
    configurationReadService: ConfigurationReadService,
    configKey: String,
    dependentChildren: Collection<LifecycleCoordinatorName>,
    managedChildren: Collection<NamedLifecycle>
): SubscriptionDominoTileBase(
    coordinatorFactory, subscriptionGenerator, subscriptionConfig, configurationReadService, configKey, dependentChildren, managedChildren
)
