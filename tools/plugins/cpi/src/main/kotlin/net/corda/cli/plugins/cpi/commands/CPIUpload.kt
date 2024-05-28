package net.corda.cli.plugins.cpi.commands

import net.corda.cli.plugins.common.RestCommand
import net.corda.restclient.CordaRestClient
import net.corda.sdk.data.Checksum
import net.corda.sdk.data.RequestId
import net.corda.sdk.packaging.CpiUploader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import java.net.URI
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

    lateinit var restClient: CordaRestClient

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
        restClient = CordaRestClient.createHttpClient(
            baseUrl = URI.create(targetUrl),
            username = username,
            password = password,
            insecure = insecure
        )
        val cpiUploadResult = try {
            sysOut.info("Uploading CPI to host: $targetUrl")
            CpiUploader(restClient).uploadCPI(
                cpi = cpi,
            ).let { RequestId(it.id) }
        } catch (e: Exception) {
            sysErr.error(e.message, e)
            logger.error("Unexpected error during CPI upload", e)
            exitProcess(2)
        }
        if (wait) {
            pollForOKStatus(cpiUploadResult)
        } else {
            sysOut.info("The ID returned from the CPI upload request is $cpiUploadResult")
        }
    }

    @Suppress("NestedBlockDepth")
    private fun pollForOKStatus(cpiUploadResult: RequestId) {
        val checksum: Checksum
        sysOut.info("Polling for result.")
        try {
            checksum = CpiUploader(restClient).cpiChecksum(
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
