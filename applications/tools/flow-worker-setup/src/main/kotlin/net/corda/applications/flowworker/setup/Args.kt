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

    @CommandLine.Option(names = ["--cpiDockerDir"], description = ["Folder containing CPIs on docker"])
    var cpiDockerDir: Path? = null

    @CommandLine.Option(names = ["--x500NName"], description = ["Optional x500 name of the holder associated with a CPI"])
    var x500NName: String = "x500Name"
}
