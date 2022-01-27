package net.corda.virtualnode.common.endpoints

import net.corda.httprpc.RpcOps
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import java.time.Duration

/** An [RpcOps] with late init properties. */
interface LateInitRPCOps : RpcOps, Lifecycle {

    /** RPC sender that handles incoming HTTP RPC requests. */
    fun createRpcSender(config: SmartConfig)

    /** Timeout for incoming HTTP RPC requests to [millis]. */
    fun setHttpRequestTimeout(httpRequestTimeout: Duration)
}