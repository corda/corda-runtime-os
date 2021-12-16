package net.corda.applications.rpc.http

import net.corda.httprpc.RpcOps
import net.corda.httprpc.client.HttpRpcClient

/**
 * Toolkit for HTTP RPC E2E tests execution
 */
interface TestToolkit {

    /**
     * Creates easily attributable to a testcase unique name
     */
    val uniqueName: String

    /**
     * Creates the [HttpRpcClient] for a given [RpcOps] class.
     */
    fun <I : RpcOps> httpClientFor(rpcOpsClass: Class<I>, userName: String = "admin", password: String = "admin"):
            HttpRpcClient<I>
}