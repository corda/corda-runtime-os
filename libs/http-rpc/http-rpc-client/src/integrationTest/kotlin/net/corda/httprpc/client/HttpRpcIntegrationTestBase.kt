package net.corda.httprpc.client

import net.corda.httprpc.security.read.impl.RPCSecurityManagerFactoryStubImpl
import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.config.models.HttpRpcContext
import java.net.ServerSocket

abstract class HttpRpcIntegrationTestBase {
    internal companion object {
        fun findFreePort() = ServerSocket(0).use { it.localPort }
        lateinit var server: HttpRpcServer
        fun isServerInitialized() = ::server.isInitialized
        const val password = "password"
        val userAlice = User("Alice", password, setOf())
        val securityManager = RPCSecurityManagerFactoryStubImpl().createRPCSecurityManager()
        val classLoader = ClassLoader.getSystemClassLoader()
        val context = HttpRpcContext("1", "api", "HttpRpcContext test title ", "HttpRpcContext test description")
    }
}