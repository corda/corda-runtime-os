package net.corda.cli.plugins.cpi.commands

import net.corda.cli.plugins.common.RestCommand
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.sdk.packaging.CpiUploader
import net.corda.sdk.rest.RestClientUtils.createRestClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine.Command
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

@Command(
    name = "list",
    description = [
        "Get the list of CPIs (Corda Package Installers) uploaded on the cluster"
    ],
    mixinStandardHelpOptions = true
)
class CPIList : RestCommand(), Runnable {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        val sysOut: Logger = LoggerFactory.getLogger("SystemOut")
        val sysErr: Logger = LoggerFactory.getLogger("SystemErr")
    }

    override fun run() {
        val restClient = createRestClient(
            CpiUploadRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )

        val result = try {
            CpiUploader().getAllCpis(
                restClient = restClient,
                wait = waitDurationSeconds.seconds
            )
        } catch (e: Exception) {
            sysErr.error(e.message, e)
            logger.error("Unexpected error during fetching the list of CPI's", e)
            exitProcess(1)
        }

        if (result.cpis.isEmpty()) {
            sysOut.info("No CPIs were uploaded on the cluster.")
        } else {
            sysOut.info("List of CPIs : ${result.cpis}")
        }
    }
}
