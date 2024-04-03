package net.corda.cli.plugins.vnode.commands

import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.typeconverter.ShortHashConverter
import net.corda.crypto.core.ShortHash
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.libs.virtualnode.maintenance.endpoints.v1.VirtualNodeMaintenanceRestResource
import net.corda.sdk.data.RequestId
import net.corda.sdk.network.VirtualNode
import net.corda.sdk.packaging.CpiUploader
import net.corda.sdk.rest.RestClientUtils.createRestClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import kotlin.time.Duration.Companion.seconds

@Command(
    name = "reset",
    description = [
        "Upload and overwrite earlier stored CPI record.",
        "The plugin purges any sandboxes running an overwritten version of a CPI and optionally ",
        "deletes vault data for the affected Virtual Nodes."
    ],
    mixinStandardHelpOptions = true
)
class ResetCommand : RestCommand(), Runnable {

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
        description = ["A list of virtual node shortIds for the vaults to be resynchronised"],
        converter = [ShortHashConverter::class]
    )
    var resync: List<ShortHash> = emptyList()

    override fun run() {
        if (resync.isNotEmpty() && !wait) {
            println("You cannot use the resync option without waiting")
            return
        }

        val cpi = File(cpiFilePath)
        if (cpi.extension != "cpi") {
            println("File type must be .cpi")
            return
        }

        val restClient = createRestClient(
            VirtualNodeMaintenanceRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        println("Uploading CPI to host: $targetUrl")
        val requestId = CpiUploader().forceCpiUpload(
            restClient = restClient,
            cpiFile = cpi.inputStream(),
            cpiName = cpi.name,
            wait = waitDurationSeconds.seconds
        ).let { RequestId(it.id) }
        if (wait) {
            pollForOKStatus(requestId)
            if (resync.isNotEmpty()) {
                resyncVaults(resync)
            }
        } else {
            println(requestId)
        }
    }

    @Suppress("NestedBlockDepth")
    private fun pollForOKStatus(virtualNodeMaintenanceResult: RequestId) {
        val restClient = createRestClient(
            CpiUploadRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        println("Polling for result.")
        CpiUploader().cpiChecksum(
            restClient = restClient,
            uploadRequestId = virtualNodeMaintenanceResult,
            wait = waitDurationSeconds.seconds
        )
        println("CPI Successfully Uploaded and applied. ")
    }

    private fun resyncVaults(virtualNodeShortIds: List<ShortHash>) {
        val restClient = createRestClient(
            VirtualNodeMaintenanceRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        val virtualNodeHelper = VirtualNode()
        try {
            virtualNodeShortIds.forEach { virtualNodeShortId ->
                virtualNodeHelper.resyncVault(restClient = restClient, holdingId = virtualNodeShortId, wait = waitDurationSeconds.seconds)
                println("Virtual node $virtualNodeShortId successfully resynced")
            }
        } catch (e: Exception) {
            println(e.message)
            logger.error("Unexpected error when resyncing vaults", e)
            return
        }
    }
}
