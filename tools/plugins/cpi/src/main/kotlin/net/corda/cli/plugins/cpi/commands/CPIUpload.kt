package net.corda.cli.plugins.cpi.commands

import net.corda.cli.plugins.common.RestCommand
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.rest.client.RestClient
import net.corda.sdk.packaging.CpiUploader
import net.corda.sdk.rest.RestClientUtils.createRestClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

@Command(
    name = "upload",
    description = [
        "Upload the CPI to the cluster"
    ],
    mixinStandardHelpOptions = true
)
class CPIUpload : RestCommand(), Runnable {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java)
        val sysOut: Logger = LoggerFactory.getLogger("SystemOut")
        val sysErr: Logger = LoggerFactory.getLogger("SystemErr")
    }

    @Option(
        names = ["-c", "--cpi"],
        required = true,
        description = ["The path to the CPI file"]
    )
    lateinit var cpiFilePath: String

    @Option(
        names = ["-w", "--wait"],
        required = false,
        description = ["wait for confirmation that CPI been accepted by the cluster"]
    )
    var wait: Boolean = false

    override fun run() {
        val cpi = File(cpiFilePath)
        if (!cpi.isFile || !cpi.exists()) {
            sysErr.error("Path: '$cpiFilePath' is not an existing file")
            exitProcess(1)
        }
        if (cpi.extension.lowercase() != "cpi") {
            sysErr.error("File type must be .cpi")
            exitProcess(1)
        }
        val restClient = createRestClient(
            CpiUploadRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        val cpiUploadResult = try {
            sysOut.info("Uploading CPI to host: $targetUrl")
            CpiUploader().uploadCPI(
                restClient = restClient,
                cpi = cpi.inputStream(),
                cpiName = cpi.name,
                wait = waitDurationSeconds.seconds
            ).id
        } catch (e: Exception) {
            sysErr.error(e.message, e)
            logger.error("Unexpected error during CPI upload", e)
            exitProcess(2)
        }
        if (wait) {
            pollForOKStatus(cpiUploadResult, restClient)
        } else {
            sysOut.info("The ID returned from the CPI upload request is $cpiUploadResult")
        }
    }

    @Suppress("NestedBlockDepth")
    private fun pollForOKStatus(cpiUploadResult: String, restClient: RestClient<CpiUploadRestResource>) {
        val checksum: String
        sysOut.info("Polling for result.")
        try {
            checksum = CpiUploader().cpiChecksum(
                restClient = restClient,
                uploadRequestId = cpiUploadResult,
                wait = waitDurationSeconds.seconds
            )
        } catch (e: Exception) {
            sysErr.error(e.message, e)
            logger.error("Unexpected error during fetching CPI checksum", e)
            exitProcess(3)
        }
        sysOut.info("CPI with checksum $checksum successfully uploaded.")
    }
}
