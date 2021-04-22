package net.corda.messaging.api.subscription

import net.corda.messaging.api.exception.CordaMessageAPIException

/**
 * A subscription that can be used to manage the life cycle of consumption of event records from a topic.
 * Events are then passed to an event processor. Events returned from the processor are produced to one or more topics.
 * Consumption of records, processing and production of new records is done atomically.
 * Subscription will begin consuming events upon start().
 * Subscription will stop consuming events and close the connection upon close()/stop()
 */
interface Subscription<K, V> : LifeCycle {

    /**
     * Start a subscription.
     * @throws CordaMessageAPIException exception thrown during the consume, process or produce stage of a subscription.
     */
    override fun start()
}

/**
 * A subscription that can be used to manage the life cycle of consumption of event records from a topic.
 * The state for a given record is retrieved and both then passed to a state + event processor.
 * State and Events returned from the processor are produced to one or more topics.
 * Consumption of records, processing and production of new records is done atomically.
 * Subscription will begin consuming events upon start().
 * Subscription will stop consuming events and close the connection upon close()/stop().
 */
interface StateAndEventSubscription<K, S, E> : LifeCycle
