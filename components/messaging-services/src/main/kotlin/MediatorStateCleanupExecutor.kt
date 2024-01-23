import net.corda.data.messaging.mediator.MediatorStates
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.utilities.debug
import net.corda.utilities.trace
import org.slf4j.LoggerFactory

/**
 * Durable processor responsible for deleting all mediator states that have terminated.
 * A terminated state is one which the message processor has returned a value of null back to the mediator for that state.
 * The mediator wrapper state is held onto for a period longer to retain the outputs from the last run of the message processor so it can
 * be replayed.
 * The job of this executor is delete these states.
 */
class MediatorStateCleanupExecutor(
    private val stateManager: StateManager
) : DurableProcessor<String, MediatorStates> {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun onNext(events: List<Record<String, MediatorStates>>): List<Record<*, *>> {
        events.mapNotNull { it.value }.forEach {
            process(it)
        }
        return listOf()
    }

    private fun process(event: MediatorStates) {
        val ids = event.states
        logger.debug { "Cleanup event received with ${ids.size} IDs to remove" }
        val states = stateManager.get(ids)
        logger.trace { "Looked up ${states.size} states" }
        val failed = stateManager.delete(states.values)
        if (failed.isNotEmpty()) {
            logger.info(
                "Failed to delete ${failed.size} mediator states when executing cleanup. Failed IDs: ${
                    failed.keys.joinToString(
                        ","
                    )
                }"
            )
        }
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<MediatorStates>
        get() = MediatorStates::class.java
}
