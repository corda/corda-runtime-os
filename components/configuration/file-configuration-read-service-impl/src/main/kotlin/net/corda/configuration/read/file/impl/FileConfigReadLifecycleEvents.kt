package net.corda.configuration.read.file.impl

import com.typesafe.config.Config
import net.corda.lifecycle.LifecycleEvent

/**
 * The bootstrap configuration has been provided.
 *
 * @param config The bootstrap configuration.
 */
internal data class BootstrapConfigProvided(val config: Config) : LifecycleEvent

/**
 * The service should create its subscription to the updates from the configuration file.
 */
internal object SetupSubscription : LifecycleEvent