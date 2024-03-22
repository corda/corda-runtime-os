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
        "get the list of cpi's uploaded in cluster"
    ],
    mixinStandardHelpOptions = true
)
class CPIList : RestCommand(), Runnable {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
        val sysOut: Logger = LoggerFactory.getLogger("SystemOut")
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
        }catch (e: Exception) {
            sysOut.info(e.message)
            logger.error("Unexpected error during fetching the list of CPI's", e)
            exitProcess(1)
        }

        if(result.cpis.isEmpty()) {
            sysOut.info("No cpi is uploaded in the cluster")
            exitProcess(2)
        } else {
            sysOut.info("List of cpi's : ${result.cpis}")
            exitProcess(3)
        }
    }


}