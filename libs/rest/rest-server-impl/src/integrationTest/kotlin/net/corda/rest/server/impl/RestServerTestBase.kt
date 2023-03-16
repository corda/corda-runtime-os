package net.corda.rest.server.impl

import net.corda.rest.server.RestServer
import net.corda.rest.server.config.models.RestContext
import net.corda.rest.test.utils.FakeSecurityManager
import net.corda.rest.test.utils.TestHttpClient

abstract class RestServerTestBase {
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