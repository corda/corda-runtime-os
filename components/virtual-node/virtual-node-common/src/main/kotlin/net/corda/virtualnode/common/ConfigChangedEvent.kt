package net.corda.virtualnode.common

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleEvent

/**
 * Lifecycle event indicating that the configuration has changed.
 *
 * This is delivered to components registering via the `registerComponent` function whenever the configuration
 * underneath one of the top level keys it requires changes. This event will only be delivered once the configuration
 * contains sections for all the top level keys the component has registered on.
 *
 * @property keys The changed keys for this config event. The first time this event is delivered it will contain all
 *                keys the component registered on. Subsequent updates will indicate which key has changed.
 * @property config Map of top-level key to config section. This is populated for all keys a component has registered on
 *                  in all cases.
 *
 * This is a copy of the net.corda.configuration.read.ConfigChangedEvent and needs to be cleaned up at the earliest convenience
 */
data class ConfigChangedEvent(val keys: Set<String>, val config: Map<String, SmartConfig>) : LifecycleEvent
