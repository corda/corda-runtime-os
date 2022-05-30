package net.corda.lifecycle.domino.logic.util

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.messaging.api.subscription.Subscription

class SubscriptionDominoTile<K, V>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    subscription: Subscription<K, V>,
    dependentChildren: Collection<LifecycleCoordinatorName>,
    managedChildren: Collection<DominoTile>
): SubscriptionDominoTileBase(coordinatorFactory, subscription, subscription.subscriptionName, dependentChildren, managedChildren)