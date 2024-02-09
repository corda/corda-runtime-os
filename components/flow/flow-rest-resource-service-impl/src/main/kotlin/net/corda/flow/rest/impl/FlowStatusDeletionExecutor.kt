package net.corda.flow.rest.impl

import net.corda.data.rest.ExecuteFlowStatusCleanup
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.utilities.debug
import org.slf4j.LoggerFactory

/**
 * Executes the deletion of stale flow status records that have been identified by the [FlowStatusCleanupProcessor].
 *
 * This executor is triggered by receiving a list of [ExecuteFlowStatusCleanup] events, each containing batches of
 * stale flow status records. It then attempts to delete these records from the StateManager. If deletion fails for any
 * of the records, the failed keys are logged but no error is thrown.
 *
 * @property stateManager The [StateManager] instance used for accessing and modifying flow status records.
 */
class FlowStatusDeletionExecutor(
    private val stateManager: StateManager
) : DurableProcessor<String, ExecuteFlowStatusCleanup> {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val keyClass: Class<String> = String::class.java
    override val valueClass: Class<ExecuteFlowStatusCleanup> = ExecuteFlowStatusCleanup::class.java

    override fun onNext(events: List<Record<String, ExecuteFlowStatusCleanup>>): List<Record<*, *>> {
        events.mapNotNull { it.value }.forEach(::process)
        return emptyList()
    }

    private fun process(event: ExecuteFlowStatusCleanup) {
        logger.debug { "FlowStatus cleanup event received with ${event.records.size} states to delete" }

        if (event.records.isEmpty()) {
            logger.debug { "FlowStatus cleanup event contained no states to delete." }
            return
        }

        val statesToDelete = event.records.map {
            State(it.key, ByteArray(0), it.version)
        }

        val failed = stateManager.delete(statesToDelete)

        if (failed.isNotEmpty()) {
            val failedKeys = failed.keys.joinToString(",")
            logger.info(
                "Failed to delete ${failed.size} FlowStatus records when executing cleanup. Failed keys: $failedKeys"
            )
        }
    }
}
