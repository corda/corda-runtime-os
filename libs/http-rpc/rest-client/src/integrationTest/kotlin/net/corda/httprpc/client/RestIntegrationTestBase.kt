package net.corda.httprpc.client

import net.corda.httprpc.server.RestServer
import net.corda.httprpc.server.config.models.RestContext
import net.corda.httprpc.test.utils.FakeSecurityManager

abstract class RestIntegrationTestBase {
    internal companion object {
        lateinit var server: RestServer
        fun isServerInitialized() = ::server.isInitialized
        val userAlice = User(FakeSecurityManager.USERNAME, FakeSecurityManager.PASSWORD, setOf())
        val securityManager = FakeSecurityManager()
        val context = RestContext("1", "api", "RestContext test title ", "RestContext test description")
    }
}