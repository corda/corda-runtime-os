package net.corda.configuration.write.impl

import net.corda.configuration.write.ConfigWriteService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleEvent

/**
 * Upon [BootstrapConfigEvent] event, [ConfigWriteService] starts processing cluster configuration updates.
 *
 * @param bootstrapConfig Config to be used by the subscription.
 */
internal data class BootstrapConfigEvent(val bootstrapConfig: SmartConfig) : LifecycleEvent