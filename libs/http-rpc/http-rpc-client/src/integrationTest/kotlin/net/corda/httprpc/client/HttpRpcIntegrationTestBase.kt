package net.corda.httprpc.client

import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.config.models.HttpRpcContext
import java.nio.file.Path

abstract class HttpRpcIntegrationTestBase {
    internal companion object {
        lateinit var server: HttpRpcServer
        fun isServerInitialized() = ::server.isInitialized
        const val password = "admin"
        val userAlice = User("admin", password, setOf())
        val securityManager = FakeSecurityManager()
        val context = HttpRpcContext("1", "api", "HttpRpcContext test title ", "HttpRpcContext test description")
        val multipartDir: Path = Path.of(System.getProperty("java.io.tmpdir"), "multipart")
    }
}