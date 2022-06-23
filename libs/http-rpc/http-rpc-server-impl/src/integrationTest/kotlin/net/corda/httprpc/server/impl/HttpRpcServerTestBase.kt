package net.corda.httprpc.server.impl

import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.config.models.HttpRpcContext
import net.corda.httprpc.test.utils.FakeSecurityManager
import net.corda.httprpc.test.utils.TestHttpClient

abstract class HttpRpcServerTestBase {
    internal companion object {
        lateinit var server: HttpRpcServer
        fun isServerInitialized() = ::server.isInitialized
        lateinit var client: TestHttpClient
        val userName = "admin"
        val password = "admin"
        val securityManager = FakeSecurityManager()
        val context = HttpRpcContext("1", "api", "HttpRpcContext test title ", "HttpRpcContext test description")
    }
}