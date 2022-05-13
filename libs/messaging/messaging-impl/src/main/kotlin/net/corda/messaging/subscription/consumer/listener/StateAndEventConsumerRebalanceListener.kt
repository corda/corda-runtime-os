package net.corda.messaging.subscription.consumer.listener

import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener

/**
 * A specialised callback interface that extends [CordaConsumerRebalanceListener] to support closing behaviour for the
 * state and event pattern.
 */
interface StateAndEventConsumerRebalanceListener: CordaConsumerRebalanceListener {

    /**
     * This method will be called when a subscription closes, this allows the rebalance listener to notify state and
     * event listeners all partitions are now lost.
     */
    fun close()
}