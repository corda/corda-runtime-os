package net.corda.lifecycle.domino.logic.util

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.LifecycleWithCoordinatorName
import net.corda.messaging.api.subscription.StateAndEventSubscription

class StateAndEventSubscriptionDominoTile<K, S, E>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    subscription: StateAndEventSubscription<K, S, E>,
    dependentChildren: Collection<LifecycleCoordinatorName>,
    managedChildren: Collection<LifecycleWithCoordinatorName>
): SubscriptionDominoTileBase(coordinatorFactory, subscription, subscription.subscriptionName, dependentChildren, managedChildren)