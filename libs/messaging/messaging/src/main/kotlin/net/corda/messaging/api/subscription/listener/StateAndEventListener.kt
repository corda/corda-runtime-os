package net.corda.messaging.api.subscription.listener

interface StateAndEventListener<K, S> {

    /**
     * List of states and keys assigned for a single partition
     */
    fun onPartitionSynced(states: Map<K, S>)

    /**
     * List of states and keys unassigned by this subscriptions consumer for a single partition
     */
    fun onPartitionLost(states: Map<K, S>)

    /**
     * List of states and keys updated as part of a single transaction
     */
    fun onPostCommit(updatedStates: Map<K, S?>)
}
