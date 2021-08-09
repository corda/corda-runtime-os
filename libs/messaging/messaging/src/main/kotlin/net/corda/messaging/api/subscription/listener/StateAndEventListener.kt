package net.corda.messaging.api.subscription.listener

interface StateAndEventListener<K, S> {
    fun onPartitionsSynced(currentStatesByPartition: MutableMap<Int, MutableMap<K, Pair<Long, S>>>)
    
    fun onPartitionLost(partitionId: Int, partitionStates: MutableMap<K, Pair<Long, S>>?)

    fun onPostCommit(updatedStates: MutableMap<K, S?>)
}
