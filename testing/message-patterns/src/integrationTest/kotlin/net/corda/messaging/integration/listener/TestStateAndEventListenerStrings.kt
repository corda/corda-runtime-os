package net.corda.messaging.integration.listener

import net.corda.messaging.api.subscription.data.TopicData
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import org.slf4j.LoggerFactory
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

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun onPartitionSynced(states: TopicData<String, String>?) {
        logger.info(states.toString())
        if (!expectedSyncState.isNullOrEmpty()) {
            if (states != null && expectedStatesPresent(expectedSyncState, states)) {
                syncPartitionLatch?.countDown()
            }
        }
    }

    private fun expectedStatesPresent(expectedSyncState: Map<String, String>, states: TopicData<String, String>) : Boolean {
        expectedSyncState.forEach {
            if (states.get(it.key) != it.value) {
                return false
            }
        }

        return true
    }

    override fun onPartitionLost(states: TopicData<String, String>?) {
        logger.info(states.toString())
        if (!expectedPartitionLostState.isNullOrEmpty()) {
            if (states != null && expectedStatesPresent(expectedPartitionLostState, states)) {
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
        logger.info(updatedStates.toString())
        onCommitCount++
    }
}
