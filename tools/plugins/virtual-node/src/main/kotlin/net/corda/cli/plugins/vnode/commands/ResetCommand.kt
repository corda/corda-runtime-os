package net.corda.cli.plugins.vnode.commands

import net.corda.cli.plugins.common.HttpRpcClientUtils.createHttpRpcClient
import net.corda.cli.plugins.common.HttpRpcCommand
import net.corda.httprpc.HttpFileUpload
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.libs.virtualnode.maintenance.endpoints.v1.VirtualNodeMaintenanceRestResource
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File

@Command(
    name = "reset",
    description = ["Upload and overwrite earlier stored CPI record.",
        "The plugin purges any sandboxes running an overwritten version of a CPI and optionally ",
        "deletes vault data for the affected Virtual Nodes."]
)
class ResetCommand : HttpRpcCommand(), Runnable {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    @Option(
        names = ["-c", "--cpi"],
        required = true,
        description = ["The path to the CPI file to reset the virtual node with."]
    )
    lateinit var cpiFilePath: String

    @Option(
        names = ["-w", "--wait"],
        required = false,
        description = ["polls for result"]
    )
    var wait: Boolean = false

    @Option(
        names = ["-r", "--resync"],
        required = false,
        description = ["A list of virtual node shortIds for the vaults to be resynchronised"]
    )
    var resync: List<String> = emptyList()

    override fun run() {
        if (resync.isNotEmpty() && !wait) {
            println("You cannot use the resync option without waiting")
            return
        }
        var virtualNodeMaintenanceResult: String
        val virtualNodeMaintenance =
            createHttpRpcClient(VirtualNodeMaintenanceRestResource::class)

        virtualNodeMaintenance.use {
            val connection = virtualNodeMaintenance.start()
            with(connection.proxy) {
                val cpi = File(cpiFilePath)
                if (cpi.extension != "cpi") {
                    println("File type must be .cpi")
                    return
                }
                try {
                    println("Uploading CPI to host: $targetUrl")
                    virtualNodeMaintenanceResult = this.forceCpiUpload(HttpFileUpload(cpi.inputStream(), cpi.name)).id
                } catch (e: Exception) {
                    println(e.message)
                    logger.error(e.stackTrace.toString())
                    return
                }
            }
        }
        if (wait) {
            pollForOKStatus(virtualNodeMaintenanceResult)
            if (resync.isNotEmpty()) {
                resyncVaults(resync)
            }
        } else {
            println(virtualNodeMaintenanceResult)
        }
    }

    @Suppress("NestedBlockDepth")
    private fun pollForOKStatus(virtualNodeMaintenanceResult: String) {
        val cpiUploadClient = createHttpRpcClient(CpiUploadRestResource::class)

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
                    logger.error(e.stackTrace.toString())
                    return
                }
            }
            println("CPI Successfully Uploaded and applied. ")
        }
    }

    private fun resyncVaults(virtualNodeShortIds: List<String>) {
        createHttpRpcClient(VirtualNodeMaintenanceRestResource::class).use { client ->
            val virtualNodeMaintenance = client.start().proxy
            try {
                virtualNodeShortIds.forEach { virtualNodeShortId ->
                    virtualNodeMaintenance.resyncVirtualNodeDb(virtualNodeShortId)
                    println("Virtual node $virtualNodeShortId successfully resynced")
                }
            } catch (e: Exception) {
                println(e.message)
                logger.error("Unexpected error when resyncing vaults", e)
                return
            }
        }
    }
}