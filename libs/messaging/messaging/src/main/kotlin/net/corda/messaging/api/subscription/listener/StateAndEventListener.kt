package net.corda.messaging.api.subscription.listener

/**
 * Client hooks that can be injected into the state and event subscription.
 */
interface StateAndEventListener<K, S> {

    /**
     * List of [states] and keys assigned to the event consumer for a single partition.
     * Any exception thrown within this method and not caught will cause the subscription to throw a fatal error.
     */
    fun onPartitionSynced(states: Map<K, S>)

    /**
     * List of [states] and keys for a partition that is unassigned from the event consumer.
     * Any exception thrown within this method and not caught will cause the subscription to throw a fatal error.
     */
    fun onPartitionLost(states: Map<K, S>)

    /**
     * List of states and keys updated as part of a single transaction.
     * State may be null. Null state indicates that the events associated with this key are completed, so the state is no longer stored.
     * Any exception thrown within this method and not caught will cause the subscription to throw a fatal error.
     */
    fun onPostCommit(updatedStates: Map<K, S?>)
}
