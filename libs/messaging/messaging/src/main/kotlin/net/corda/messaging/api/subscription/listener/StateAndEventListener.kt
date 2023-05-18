package net.corda.messaging.api.subscription.listener

import net.corda.messaging.api.subscription.data.TopicData

/**
 * Client hooks that can be injected into the state and event subscription.
 * This API allows for the client to keep track of all changes to the in-memory map of states used by the state and event subscription.
 */
interface StateAndEventListener<K: Any, S: Any> {

    /**
     * List of [states] and keys assigned to the event consumer for a single partition.
     * This will be triggered after a new partition is assigned and all new states from this partition have been read.
     * If the new partition has no states this method will not be called.
     * Any exception thrown within this method and not caught will cause the subscription to throw a fatal error.
     */
    fun onPartitionSynced(states: TopicData<K, S>?)

    /**
     * List of [states] and keys for a partition that is unassigned from the event consumer.
     * Any exception thrown within this method and not caught will cause the subscription to throw a fatal error.
     */
    fun onPartitionLost(states: TopicData<K, S>?)

    /**
     * List of states and keys updated as part of a single transaction.
     * [updatedStates] will contain all states updated after the transaction is successful.
     * State may be null. Null state indicates that the events associated with this key are completed, so the state is no longer stored.
     * Any exception thrown within this method and not caught will cause the subscription to throw a fatal error.
     */
    fun onPostCommit(updatedStates: Map<K, S?>)
}
