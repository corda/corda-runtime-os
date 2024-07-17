package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestCommand
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File

@Command(
    name = "upgrade-cpi",
    description = ["Upgrade the CPI used in a network"], // TODO: add more description
    mixinStandardHelpOptions = true,
)
class UpgradeCpi : Runnable, RestCommand() {
    @Option(
        names = ["--cpi-file"],
        description = ["Location of the CPI file to upgrade the members to."],
        required = true,
    )
    lateinit var cpiFile: File

    @Option(
        names = ["--network-config-file"],
        description = ["Location of the network configuration file."], // TODO: add more description
        required = true,
    )
    lateinit var networkConfigFile: File

    override fun run() {
        super.run()
        upgradeCpi()
    }

    private fun upgradeCpi() {
        require(cpiFile.isFile) { "CPI file '$cpiFile' not found." }
        require(networkConfigFile.isFile) { "Network configuration file '$cpiFile' not found." }
        TODO("Not implemented yet")
    }
}
