package net.corda.virtualnode.common.endpoints

import net.corda.httprpc.RpcOps
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import java.time.Duration

/** An [RpcOps] with late init properties. */
interface LateInitRPCOps : RpcOps, Lifecycle {

    /** Creates and starts RPC sender. RPC sender puts RPC requests to Kafka. */
    fun createAndStartRpcSender(config: SmartConfig)

    /** Sets timeout for RPC requests. */
    fun setRpcRequestTimeout(rpcRequestTimeout: Duration)
}