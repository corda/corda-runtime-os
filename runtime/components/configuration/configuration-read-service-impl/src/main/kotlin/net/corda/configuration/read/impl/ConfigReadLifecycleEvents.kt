package net.corda.configuration.read.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleEvent

/**
 * The bootstrap configuration has been provided.
 *
 * @param config The bootstrap configuration.
 */
internal data class BootstrapConfigProvided(val config: SmartConfig) : LifecycleEvent

/**
 * The service should create its subscription to the message bus.
 */
internal class SetupAvroSchemaSubscription : LifecycleEvent

/**
 * The service should create its subscription to the message bus.
 */
internal class SetupConfigSubscription : LifecycleEvent

/**
 * New configuration has been received
 *
 * @param config A map of changed keys to changed configuration.
 */
internal data class NewConfigReceived(val config: Map<String, SmartConfig>) : LifecycleEvent

/**
 * A new configuration change handler has been added by another component
 *
 * @param registration The configuration registration that has been created.
 */
internal data class ConfigRegistrationAdd(val registration: ConfigurationChangeRegistration) : LifecycleEvent

/**
 * A configuration change handler has been removed by another component
 *
 * @param registration The removed registration
 */
internal data class ConfigRegistrationRemove(val registration: ConfigurationChangeRegistration) : LifecycleEvent