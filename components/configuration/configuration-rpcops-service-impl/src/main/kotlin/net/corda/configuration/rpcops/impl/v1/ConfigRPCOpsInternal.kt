package net.corda.configuration.rpcops.impl.v1

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.endpoints.v1.ConfigRPCOps
import net.corda.lifecycle.Lifecycle

/** Extends [ConfigRPCOps] with methods to set up the RPC sender for handling incoming requests. */
internal interface ConfigRPCOpsInternal : ConfigRPCOps, Lifecycle {
    /** Starts the RPC sender that handles incoming HTTP RPC requests using the given [config]. */
    fun createAndStartRPCSender(config: SmartConfig)

    /** Sets the timeout for incoming HTTP RPC requests to [millis]. */
    fun setTimeout(millis: Int)
}