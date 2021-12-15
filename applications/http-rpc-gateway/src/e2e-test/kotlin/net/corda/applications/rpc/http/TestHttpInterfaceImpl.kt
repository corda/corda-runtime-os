package net.corda.applications.rpc.http

import net.corda.httprpc.RpcOps
import net.corda.httprpc.client.HttpRpcClient
import net.corda.httprpc.client.config.HttpRpcClientConfig
import java.util.concurrent.atomic.AtomicInteger

class TestHttpInterfaceImpl(private val testCaseClass: Class<Any>, private val baseAddress: String) : TestHttpInterface {

    private val counter = AtomicInteger()

    /**
     * Good unique name will be:
     * "$testCaseClass-counter-currentTimeMillis"
     * [testCaseClass] will ensure traceability of the call, [counter] will help to avoid clashes within the same
     * testcase run and `currentTimeMillis` will provision for re-runs of the same test without wiping the database.
     */
    override val uniqueName: String
        get() = "${testCaseClass.simpleName}-${counter.incrementAndGet()}-${System.currentTimeMillis()}"

    override fun <I : RpcOps> clientFor(rpcOpsClass: Class<I>, userName: String, password: String): HttpRpcClient<I> {
        return HttpRpcClient(
            baseAddress, rpcOpsClass, HttpRpcClientConfig()
                .enableSSL(true)
                .minimumServerProtocolVersion(1)
                .username(userName)
                .password(password)
        )
    }
}