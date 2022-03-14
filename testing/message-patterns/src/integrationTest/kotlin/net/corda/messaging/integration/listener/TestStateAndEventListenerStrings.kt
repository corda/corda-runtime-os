package net.corda.messaging.integration.listener

import net.corda.messaging.api.subscription.listener.StateAndEventListener
import java.util.concurrent.CountDownLatch

class TestStateAndEventListenerStrings(
    private val expectedCommitState: List<Map<String, String>>? = null,
    private val commitStateLatch: CountDownLatch? = null,
    private val delayOnCommit: Long? = null,
    private val expectedSyncState: Map<String, String>? = null,
    private val syncPartitionLatch: CountDownLatch? = null,
    private val expectedPartitionLostState: Map<String, String>? = null,
    private val losePartitionLatch: CountDownLatch? = null,
) : StateAndEventListener<String, String> {

    private var onCommitCount = 0

    override fun onPartitionSynced(states: Map<String, String>) {
        println("======== SYNCED PARTITION =======")
        println(states.toString())
        if (!expectedSyncState.isNullOrEmpty()) {
            if (states == expectedSyncState) {
                syncPartitionLatch?.countDown()
            }
        }
    }

    override fun onPartitionLost(states: Map<String, String>) {
        println("======== PARTITION LOST =======")
        println(states.toString())
        if (!expectedPartitionLostState.isNullOrEmpty()) {
            if (states == expectedPartitionLostState) {
                losePartitionLatch?.countDown()
            }
        }
    }

    override fun onPostCommit(updatedStates: Map<String, String?>) {
        if (delayOnCommit != null) {
            Thread.sleep(delayOnCommit)
        }

        if (expectedCommitState?.contains(updatedStates) == true) {
            commitStateLatch?.countDown()
        }
        println("======== COMMIT =======")
        println(updatedStates.toString())
        onCommitCount++
    }
}
