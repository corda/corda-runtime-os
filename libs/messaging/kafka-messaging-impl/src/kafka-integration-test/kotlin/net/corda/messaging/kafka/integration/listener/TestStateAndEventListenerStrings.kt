package net.corda.messaging.kafka.integration.listener

import net.corda.messaging.api.subscription.listener.StateAndEventListener

class TestStateAndEventListenerStrings : StateAndEventListener<String, String> {
    override fun onPartitionSynced(states: Map<String, String>) {
        println("======== SYNCED PARTITION =======")
        println(states.toString())
    }

    override fun onPartitionLost(states: Map<String, String>) {
        println("======== PARTITION LOST =======")
        println(states.toString())
    }

    override fun onPostCommit(updatedStates: Map<String, String?>) {
        println("======== COMMIT =======")
        println(updatedStates.toString())
    }
}