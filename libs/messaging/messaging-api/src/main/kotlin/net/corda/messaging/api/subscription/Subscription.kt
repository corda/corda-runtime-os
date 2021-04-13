package net.corda.messaging.api.subscription

/**
 * A subscription that can be used to manage the life cycle of an  event processor
 */
interface Subscription<K, V> : LifeCycle

/**
 * A subscription that can be used to manage the life cycle of state + event processor.
 */
interface StateAndEventSubscription<K, S, E> : LifeCycle
