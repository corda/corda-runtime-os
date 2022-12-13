package net.corda.applications.flowworker.setup


import picocli.CommandLine
import java.io.File
import java.nio.file.Path

class Args {
    @CommandLine.Parameters(
        paramLabel = "<task>",
        description = ["List of tasks to run in order"]
    )
    var tasks: List<String> = listOf()

    @CommandLine.Option(names = ["--config"], description = ["File containing configuration to be stored"])
    var configurationFile: File? = null

    @CommandLine.Option(names = ["--cpiDir"], description = ["Folder containing CPIs"])
    var cpiDir: Path? = null

    @CommandLine.Option(
        names = ["--x500Name"],
        description = ["Optional x500 name of the holder associated with a CPI"]
    )
    var x500NName: String = "CN=Bob, O=Bob Corp, L=LDN, C=GB"

    @CommandLine.Option(names = ["--bootstrapServer"], description = ["Optional kafka bootstrap server address"])
    var bootstrapServer: String = "localhost:9092"

    @CommandLine.Option(names = ["--instanceId"], description = ["Optional kafka instanceId"])
    var instanceId: String = "1"

    @CommandLine.Option(names = ["--topicPrefix"], description = ["Optional kafka topic prefix"])
    var topicPrefix: String = ""

    @CommandLine.Option(names = ["--flowName"], description = ["Named flow to start"])
    var flowName: String = "default"

    @CommandLine.Option(names = ["--shortHolderId"], description = ["Virtual Node Short Hash"])
    var shortHolderId: String = ""

    @CommandLine.Option(names = ["--tokenCcy"], description = ["Currency to use for token query"])
    var tokenCcy: String = "USD"

    @CommandLine.Option(names = ["--targetAmount"], description = ["List of target amounts for token selection"])
    var targetAmount: List<Long> = listOf()
}
