package net.corda.cli.plugins.vnode.commands

import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.RpcOps
import net.corda.httprpc.client.HttpRpcClient
import net.corda.httprpc.client.config.HttpRpcClientConfig
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRPCOps
import net.corda.libs.virtualnode.maintenance.endpoints.v1.VirtualNodeMaintenanceRPCOps

import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import kotlin.reflect.KClass

@Command(
    name = "reset",
    description = ["Upload and overwrite earlier stored CPI record.",
        "Any sandboxes running an overwritten version of CPI will be purged and optionally",
        "vault data for the affected Virtual Nodes wiped out."]
)
class ResetCommand : Runnable {

    @Option(
        names = ["-t", "--target"],
        required = true,
        description = ["The base address of the server (including port)."]
    )
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

    @Option(names = ["-w", "--wait"], required = false, description = ["polls for result"])
    var wait: Boolean = false

    override fun run() {
        var virtualNodeMaintenanceResult: String
        val virtualNodeMaintenance = createHttpRpcClient(VirtualNodeMaintenanceRPCOps::class)

        virtualNodeMaintenance.use {
            val connection = virtualNodeMaintenance.start()
            with(connection.proxy) {
                val cpi = File(cpiFileName)
                if (cpi.extension != "cpi") {
                    println("File type must be .cpi")
                    return
                }
                try {
                    println("Uploading CPI to host: $targetUrl")
                    virtualNodeMaintenanceResult = this.forceCpiUpload(HttpFileUpload(cpi.inputStream(), cpi.name)).id
                } catch (e: Exception) {
                    println(e.message)
                    return
                }
            }
        }
        if (wait) {
            pollForOKStatus(virtualNodeMaintenanceResult)
        } else {
            println(virtualNodeMaintenanceResult)
        }
    }

    @Suppress("NestedBlockDepth")
    private fun pollForOKStatus(virtualNodeMaintenanceResult: String) {
        val cpiUploadClient = createHttpRpcClient(CpiUploadRPCOps::class)

        cpiUploadClient.use {
            val connection = cpiUploadClient.start()
            with(connection.proxy) {
                println("Polling for result.")
                try {
                    while (this.status(virtualNodeMaintenanceResult).status != "OK") {
                        Thread.sleep(5000L)
                    }
                } catch (e: Exception) {
                    println(e.message)
                    return
                }
            }
            println("CPI Successfully Uploaded and applied. ")
        }
    }



    private fun <I : RpcOps> createHttpRpcClient(rpcOps: KClass<I>): HttpRpcClient<I> {
        return HttpRpcClient(
            baseAddress = "$targetUrl/api/v1/",
            rpcOps.java,
            HttpRpcClientConfig()
                .enableSSL(useSSL)
                .minimumServerProtocolVersion(minimumServerProtocolVersion)
                .username(username)
                .password(password),
            healthCheckInterval = 500
        )
    }
}