package net.corda.db.connection.manager.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleEvent

/**
 * The bootstrap configuration has been provided.
 *
 * @param config The bootstrap configuration.
 */
internal data class BootstrapConfigProvided(val config: SmartConfig) : LifecycleEvent