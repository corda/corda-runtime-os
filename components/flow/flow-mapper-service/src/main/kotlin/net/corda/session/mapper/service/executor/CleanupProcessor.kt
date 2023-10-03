package net.corda.session.mapper.service.executor

import net.corda.data.flow.event.mapper.ExecuteCleanup
import net.corda.libs.statemanager.api.StateManager
import net.corda.utilities.debug
import net.corda.utilities.trace
import org.slf4j.LoggerFactory

class CleanupProcessor(
    private val stateManager: StateManager
) {
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun process(event: ExecuteCleanup) {
        logger.debug { "Cleanup event received with ${event.ids.size} IDs to remove" }
        val states = stateManager.get(event.ids)
        logger.trace { "Looked up ${states.size} states" }
        val failed = stateManager.delete(states.values)
        if (failed.isNotEmpty()) {
            logger.info(
                "Failed to delete ${failed.size} mapper states when executing a cleanup event. Failed IDs: ${
                    failed.keys.joinToString(
                        ","
                    )
                }"
            )
        }
    }
}