package net.corda.lifecycle.domino.logic.util

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.subscription.Subscription

class SubscriptionDominoTile<K, V>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    subscription: Subscription<K, V>,
    dependentChildren: Collection<LifecycleCoordinatorName>,
    managedChildren: Collection<Lifecycle>
): SubscriptionDominoTileBase(coordinatorFactory, subscription, subscription.subscriptionName, dependentChildren, managedChildren)