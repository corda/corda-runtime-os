package net.corda.cli.plugins.cpiupload.commands

import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.cli.plugins.common.RestCommand
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.rest.HttpFileUpload
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File

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
        description = ["polls for result"]
    )
    var wait: Boolean = false

    override fun run() {
        var cpiUploadResult: String
        val cpiUpload =
            createRestClient(CpiUploadRestResource::class)

        cpiUpload.use {
            val connection = cpiUpload.start()
            with(connection.proxy) {
                val cpi = File(cpiFilePath)
                if (cpi.extension != "cpi") {
                    println("File type must be .cpi")
                    return
                }
                try {
                    println("Uploading CPI to host: $targetUrl")
                    cpiUploadResult = this.cpi(HttpFileUpload(cpi.inputStream(), cpi.name)).id
                } catch (e: Exception) {
                    println(e.message)
                    logger.error(e.stackTrace.toString())
                    return
                }
            }
        }
        if (wait) {
            pollForOKStatus(cpiUploadResult)
        } else {
            println(cpiUploadResult)
        }
    }

    @Suppress("NestedBlockDepth")
    private fun pollForOKStatus(cpiUploadResult: String) {
        val cpiUploadClient = createRestClient(CpiUploadRestResource::class)

        cpiUploadClient.use {
            val connection = cpiUploadClient.start()
            with(connection.proxy) {
                println("Polling for result.")
                try {
                    while (this.status(cpiUploadResult).status != "OK") {
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
}
