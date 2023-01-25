package net.corda.httprpc.server.impl

import net.corda.httprpc.server.RestServer
import net.corda.httprpc.server.config.models.RestContext
import net.corda.httprpc.test.utils.FakeSecurityManager
import net.corda.httprpc.test.utils.TestHttpClient

abstract class HttpRpcServerTestBase {
    internal companion object {
        lateinit var server: RestServer
        fun isServerInitialized() = ::server.isInitialized
        lateinit var client: TestHttpClient
        const val userName = FakeSecurityManager.USERNAME
        const val password = FakeSecurityManager.PASSWORD
        val securityManager = FakeSecurityManager()
        val context = RestContext("1", "api", "RestContext test title ", "RestContext test description")
    }
}