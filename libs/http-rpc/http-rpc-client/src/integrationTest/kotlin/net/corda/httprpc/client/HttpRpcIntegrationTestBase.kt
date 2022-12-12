package net.corda.httprpc.client

import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.config.models.HttpRpcContext
import net.corda.httprpc.test.utils.FakeSecurityManager

abstract class HttpRpcIntegrationTestBase {
    internal companion object {
        lateinit var server: HttpRpcServer
        fun isServerInitialized() = ::server.isInitialized
        val userAlice = User(FakeSecurityManager.USERNAME, FakeSecurityManager.PASSWORD, setOf())
        val securityManager = FakeSecurityManager()
        val context = HttpRpcContext("1", "api", "HttpRpcContext test title ", "HttpRpcContext test description")
    }
}