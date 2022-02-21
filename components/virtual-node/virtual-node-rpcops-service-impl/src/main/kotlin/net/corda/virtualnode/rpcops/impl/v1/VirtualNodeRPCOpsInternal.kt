package net.corda.virtualnode.rpcops.impl.v1

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRPCOps
import net.corda.lifecycle.Lifecycle

/** Extends [VirtualNodeRPCOps] with methods to set up the RPC sender for handling incoming requests. */
internal interface VirtualNodeRPCOpsInternal : VirtualNodeRPCOps, Lifecycle {
    /** Starts the RPC sender that handles incoming HTTP RPC requests using the given [config]. */
    fun createAndStartRpcSender(config: SmartConfig)

    /** Sets the timeout for incoming HTTP RPC requests to [millis]. */
    fun setTimeout(millis: Int)
}