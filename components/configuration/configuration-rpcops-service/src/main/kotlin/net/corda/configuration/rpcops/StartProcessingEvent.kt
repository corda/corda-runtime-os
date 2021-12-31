package net.corda.configuration.rpcops

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleEvent

/**
 * Indicates that the [ConfigRPCOpsService] should start handling RPC operations related to cluster configuration
 * management.
 *
 * @param config Config to be used by the RPC sender.
 */
internal class StartProcessingEvent(val config: SmartConfig) : LifecycleEvent