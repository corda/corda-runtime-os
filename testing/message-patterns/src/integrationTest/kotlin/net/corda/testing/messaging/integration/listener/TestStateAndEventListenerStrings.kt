package net.corda.testing.messaging.integration.listener

import net.corda.messaging.api.subscription.listener.StateAndEventListener
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch

@Suppress("LongParameterList")
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

    override fun onPartitionSynced(states: Map<String, String>) {
        logger.info("======== SYNCED PARTITION =======")
        logger.info(states.toString())
        if (!expectedSyncState.isNullOrEmpty()) {
            if (states == expectedSyncState) {
                syncPartitionLatch?.countDown()
            }
        }
    }

    override fun onPartitionLost(states: Map<String, String>) {
        logger.info("======== PARTITION LOST =======")
        logger.info(states.toString())
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
        logger.info("======== COMMIT =======")
        logger.info(updatedStates.toString())
        onCommitCount++
    }
}
