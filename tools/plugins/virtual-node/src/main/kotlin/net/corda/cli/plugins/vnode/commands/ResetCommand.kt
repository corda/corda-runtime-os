package net.corda.cli.plugins.vnode.commands

import net.corda.cli.api.serviceUsers.HttpServiceUser
import net.corda.cli.api.services.HttpService
import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.client.HttpRpcClient
import net.corda.httprpc.client.config.HttpRpcClientConfig
import net.corda.libs.virtualnode.maintenance.endpoints.v1.VirtualNodeMaintenanceRPCOps
import picocli.CommandLine
import picocli.CommandLine.Mixin
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import java.io.InputStream


@Command(
    name = "reset",
    description = ["to do"]
)
class ResetCommand : Runnable {

    @Option(names = ["-t", "--target"], required = true, description = ["The base address of the server (including port)."])
    lateinit var targetUrl: String

    @Option(names = ["-u", "--user"], description = ["User name"], required = true)
    lateinit var username: String

    @Option(names = ["-p", "--password"], description = ["Password"], required = true)
    lateinit var password: String

    @Option(names = ["-ssl", "--ssl"], required = false, description = ["Use SSL."])
    var useSSL: Boolean = true

    @Option(names = ["-pv", "--protocol-version"], required = false, description = ["Minimum protocol version."])
    var minimumServerProtocolVersion: Int = 1

    @Option(names = ["-c", "--cpi"], required = true, description = ["CPI file to reset the virtual node with."])
    lateinit var cpiFileName: String

    override fun run() {
        val client = HttpRpcClient(
            baseAddress = "$targetUrl/api/v1/",
            VirtualNodeMaintenanceRPCOps::class.java,
            HttpRpcClientConfig()
                .enableSSL(useSSL)
                .minimumServerProtocolVersion(minimumServerProtocolVersion)
                .username(username)
                .password(password),
            healthCheckInterval = 500
        )

        client.use {
            val connection = client.start()

            with(connection.proxy) {
                val cpi = File(cpiFileName)
                val result = this.forceCpiUpload(HttpFileUpload(cpi.inputStream(), cpi.name))
                print(result.id)
            }
        }
    }
}