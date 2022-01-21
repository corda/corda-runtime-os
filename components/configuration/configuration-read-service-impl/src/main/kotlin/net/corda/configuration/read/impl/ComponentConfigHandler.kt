package net.corda.configuration.read.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationHandler
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator

/**
 * A standard configuration handler for components.
 *
 * This handler waits until all required keys are present before delivering configuration updates. It also filters down
 * the configuration map to only deliver those configuration sections that are relevant to the component.
 */
class ComponentConfigHandler(private val coordinator: LifecycleCoordinator, private val requiredKeys: Set<String>) :
    ConfigurationHandler {

    private var snapshotPosted = false

    override fun onNewConfiguration(changedKeys: Set<String>, config: Map<String, SmartConfig>) {
        if (requiredKeys.all { it in config.keys } and changedKeys.any { it in requiredKeys }) {
            val keys = if (snapshotPosted) {
                changedKeys.filter { it in requiredKeys }.toSet()
            } else {
                snapshotPosted = true
                requiredKeys
            }
            val event = ConfigChangedEvent(keys, config.filter { it.key in requiredKeys })
            coordinator.postEvent(event)
        }
    }
}