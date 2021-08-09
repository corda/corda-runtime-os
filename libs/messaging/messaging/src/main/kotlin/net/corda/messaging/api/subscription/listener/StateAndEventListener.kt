package net.corda.messaging.api.subscription.listener

interface StateAndEventListener<K, S> {
    fun onPartitionsSynced(currentStatesByPartition: Map<Int, Map<K, Pair<Long, S?>>>)
    
    fun onPartitionLost(partitionId: Int, partitionStates: Map<K, Pair<Long, S?>>)

    fun onPostCommit(updatedStates: MutableMap<Int, MutableMap<K, S?>>)
}
