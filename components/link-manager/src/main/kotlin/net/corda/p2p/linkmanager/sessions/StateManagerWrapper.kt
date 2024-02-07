package net.corda.p2p.linkmanager.sessions

import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class StateManagerWrapper(
    private val stateManager: StateManager,
    private val sessionExpiryScheduler: SessionExpiryScheduler,
) {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(StateManagerWrapper::class.java)

    }
    fun get(
        keys: Collection<String>,
    ) = sessionExpiryScheduler.checkStatesValidateAndRememberThem(
        stateManager.get(keys),
    )

    fun findStatesMatchingAny(
        filters: Collection<MetadataFilter>,
    ) = sessionExpiryScheduler.checkStatesValidateAndRememberThem(
        stateManager.findByMetadataMatchingAny(filters),
    )

    fun upsert(
        changes: Collection<StateManagerAction>,
    ): Map<String, State?> {
        val updates = changes.filterIsInstance<UpdateAction>()
            .map {
                it.state
            }.mapNotNull {
                sessionExpiryScheduler.checkStateValidateAndRememberIt(
                    state = it,
                    beforeUpdate = true,
                )
            }
        val creates = changes.filterIsInstance<CreateAction>()
            .map {
                it.state
            }.mapNotNull {
                sessionExpiryScheduler.checkStateValidateAndRememberIt(it)
            }
        val failedUpdates = if (updates.isNotEmpty()) {
            stateManager.update(updates).onEach {
                logger.info("Failed to update the state of session with ID ${it.key}")
            }
        } else {
            emptyMap()
        }
        val failedCreates = if (creates.isNotEmpty()) {
            stateManager.create(creates).associateWith {
                logger.info("Failed to create the state of session with ID $it")
                null
            }
        } else {
            emptyMap()
        }
        return failedUpdates + failedCreates
    }
}