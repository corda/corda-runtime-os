package net.corda.messaging.mediator.processor

import net.corda.libs.statemanager.api.State
import java.util.concurrent.ConcurrentHashMap

/**
 * Track the shared state updated by the various mediator processors
 */
data class StatesToPersist(
    val statesToCreate: ConcurrentHashMap<String, State?> = ConcurrentHashMap<String, State?>(),
    val statesToUpdate: ConcurrentHashMap<String, State?> = ConcurrentHashMap<String, State?>(),
    val statesToDelete: ConcurrentHashMap<String, State?> = ConcurrentHashMap<String, State?>(),
) {
    fun clear() {
        statesToCreate.clear()
        statesToUpdate.clear()
        statesToDelete.clear()
    }
}