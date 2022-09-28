package net.corda.cli.plugins.common

import net.corda.httprpc.RpcOps
import net.corda.httprpc.client.HttpRpcClient
import net.corda.httprpc.client.HttpRpcConnection
import net.corda.httprpc.client.config.HttpRpcClientConfig
import kotlin.reflect.KClass

object HttpRpcClientUtils {

    fun <I : RpcOps> HttpRpcCommand.createHttpRpcClient(rpcOps: KClass<I>): HttpRpcClient<I> {
        val localTargetUrl = if(targetUrl.endsWith("/")) {
            targetUrl.dropLast(1)
        } else {
            targetUrl
        }
        return HttpRpcClient(
            baseAddress = "$localTargetUrl/api/v1/",
            rpcOps.java,
            HttpRpcClientConfig()
                .enableSSL(true)
                .minimumServerProtocolVersion(minimumServerProtocolVersion)
                .username(username)
                .password(password),
            healthCheckInterval = 500
        )
    }

    fun <I : RpcOps> HttpRpcClient<I>.startAndWait(waitDurationSeconds: Int): HttpRpcConnection<I> {
        val endTime = System.currentTimeMillis() + waitDurationSeconds * 1000

        var lastException: Exception?

        do {
            try {
                return this.start()
            } catch (ex: Exception) {
                lastException = ex
                Thread.sleep(1000)
            }
        } while (System.currentTimeMillis() <= endTime)

        throw lastException!!
    }
}