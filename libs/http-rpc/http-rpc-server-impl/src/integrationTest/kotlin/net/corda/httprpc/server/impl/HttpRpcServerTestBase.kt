package net.corda.httprpc.server.impl

import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.config.models.HttpRpcContext
import net.corda.httprpc.server.impl.utils.TestHttpClient
import java.net.ServerSocket

abstract class HttpRpcServerTestBase {
    internal companion object {
        lateinit var server: HttpRpcServer
        fun isServerInitialized() = ::server.isInitialized
        lateinit var client: TestHttpClient
        val userName = "admin"
        val password = "admin"
        fun findFreePort() = ServerSocket(0).use { it.localPort }
        val securityManager = FakeSecurityManager()
        val context = HttpRpcContext("1", "api", "HttpRpcContext test title ", "HttpRpcContext test description")
    }
}