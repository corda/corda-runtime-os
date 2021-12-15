package net.corda.applications.rpc.http

import net.corda.httprpc.RpcOps
import net.corda.httprpc.client.HttpRpcClient

interface TestHttpInterface {

    /**
     * Creates easily attributable to a testcase unique name
     */
    val uniqueName: String

    /**
     * Creates the [HttpRpcClient] for a given [RpcOps] class.
     */
    fun <I : RpcOps> clientFor(rpcOpsClass: Class<I>, userName: String = "admin", password: String = "admin"):
            HttpRpcClient<I>
}