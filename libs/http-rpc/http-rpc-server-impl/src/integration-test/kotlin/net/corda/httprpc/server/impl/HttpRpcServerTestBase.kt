package net.corda.httprpc.server.impl

import net.corda.httprpc.security.read.impl.RPCSecurityManagerFactoryStubImpl
import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.config.models.HttpRpcContext
import net.corda.httprpc.server.impl.utils.TestHttpClient


abstract class HttpRpcServerTestBase {
    internal companion object {
        lateinit var server: HttpRpcServer
        fun isServerInitialized() = ::server.isInitialized
        lateinit var client: TestHttpClient
        val userName = "admin"
        val password = "admin"
        const val serverPort = 11000 //TODO introduce port allocator
        val securityManager = RPCSecurityManagerFactoryStubImpl().createRPCSecurityManager()
        val classLoader = ClassLoader.getSystemClassLoader()
        val context = HttpRpcContext("1", "api", "HttpRpcContext test title ", "HttpRpcContext test description")
    }
}