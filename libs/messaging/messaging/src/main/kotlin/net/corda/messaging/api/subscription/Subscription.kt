package net.corda.messaging.api.subscription

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
     */
    override fun start()

    /**
     * Check the state of a subscription. true if subscription is still active. false otherwise.
     */
    val isRunning: Boolean
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
