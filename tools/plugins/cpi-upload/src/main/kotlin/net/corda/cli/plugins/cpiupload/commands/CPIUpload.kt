package net.corda.cli.plugins.cpiupload.commands

import net.corda.cli.plugins.common.RestCommand
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.sdk.packaging.CpiUploader
import net.corda.sdk.rest.RestClientUtils.createRestClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
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
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
        val sysOut: Logger = LoggerFactory.getLogger("SystemOut")
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
        lateinit var cpiUploadResult: String
        val cpi = File(cpiFilePath)
        if (cpi.extension.lowercase() != "cpi") {
            sysOut.info("File type must be .cpi")
            System.exit(1)
        }
        val restClient = createRestClient(
            CpiUploadRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        try {
            sysOut.info("Uploading CPI to host: $targetUrl")
            cpiUploadResult = CpiUploader().uploadCPI(
                restClient = restClient,
                cpi = cpi.inputStream(),
                cpiName = cpi.name,
                wait = waitDurationSeconds.seconds
            ).id
        } catch (e: Exception) {
            sysOut.info(e.message)
            logger.error("Unexpected error during CPI upload", e)
            System.exit(2)
        }
        if (wait) {
            pollForOKStatus(cpiUploadResult)
        } else {
            sysOut.info(cpiUploadResult)
        }
    }

    @Suppress("NestedBlockDepth")
    private fun pollForOKStatus(cpiUploadResult: String) {
        val restClient = createRestClient(
            CpiUploadRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        sysOut.info("Polling for result.")
        try {
            CpiUploader().cpiChecksum(
                restClient = restClient,
                uploadRequestId = cpiUploadResult,
                wait = waitDurationSeconds.seconds
            )
        } catch (e: Exception) {
            sysOut.info(e.message)
            logger.error("Unexpected error during fetching CPI checksum", e)
            return System.exit(3)
        }
        sysOut.info("CPI Successfully Uploaded and applied.")
    }
}
