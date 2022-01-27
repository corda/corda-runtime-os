package net.corda.cpi.upload.endpoints.internal

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.virtualnode.endpoints.v1.CpiUploadRPCOps
import net.corda.lifecycle.Lifecycle

/** Extends [CpiUploadRPCOps] with methods to set up the RPC sender for handling incoming requests. */
interface CpiUploadRPCOpsInternal : CpiUploadRPCOps, Lifecycle {
    /** Starts the RPC sender that handles incoming HTTP RPC requests using the given [config]. */
    fun createAndStartRPCSender(config: SmartConfig)

    /** Sets the timeout for incoming HTTP RPC requests to [millis]. */
    fun setTimeout(millis: Int)
}