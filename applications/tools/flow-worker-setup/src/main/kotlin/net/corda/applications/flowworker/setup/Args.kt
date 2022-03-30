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

    @CommandLine.Option(names = ["--x500Name"], description = ["Optional x500 name of the holder associated with a CPI"])
    var x500NName: String = "CN=Bob, O=Bob Corp, L=LDN, C=GB"

    @CommandLine.Option(names = ["--bootstrapServer"], description = ["Optional kafka bootstrap server address"])
    var bootstrapServer: String = "localhost:9092"

    @CommandLine.Option(names = ["--instanceId"], description = ["Optional kafka instanceId"])
    var instanceId: String = "1"

    @CommandLine.Option(names = ["--topicPrefix"], description = ["Optional kafka topic prefix"])
    var topicPrefix: String = ""
}
