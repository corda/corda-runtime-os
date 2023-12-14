package net.corda.rest.client

import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.server.RestServer
import net.corda.rest.server.config.models.RestContext
import net.corda.rest.test.utils.FakeSecurityManager

abstract class RestIntegrationTestBase {
    internal companion object {
        lateinit var server: RestServer
        fun isServerInitialized() = ::server.isInitialized
        val userAlice = User(FakeSecurityManager.USERNAME, FakeSecurityManager.PASSWORD, setOf())
        val securityManager = FakeSecurityManager()
        val context = RestContext("api", "RestContext test title ", "RestContext test description")
        val apiVersion = RestApiVersion.C5_1
    }
}
