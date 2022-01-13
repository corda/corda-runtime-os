package net.corda.httprpc.client

import net.corda.httprpc.security.read.impl.RPCSecurityManagerFactoryStubImpl
import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.config.models.HttpRpcContext

abstract class HttpRpcIntegrationTestBase {
    internal companion object {
        lateinit var server: HttpRpcServer
        fun isServerInitialized() = ::server.isInitialized
        const val password = "admin"
        val userAlice = User("admin", password, setOf())
        val securityManager = RPCSecurityManagerFactoryStubImpl().createRPCSecurityManager()
        val context = HttpRpcContext("1", "api", "HttpRpcContext test title ", "HttpRpcContext test description")
    }
}