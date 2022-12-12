package net.corda.applications.workers.rpc.http

import net.corda.applications.workers.rpc.utils.AdminPasswordUtil.adminPassword
import net.corda.applications.workers.rpc.utils.AdminPasswordUtil.adminUser
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
    fun <I : RpcOps> httpClientFor(
        rpcOpsClass: Class<I>,
        userName: String = adminUser,
        password: String = adminPassword
    ): HttpRpcClient<I>
}