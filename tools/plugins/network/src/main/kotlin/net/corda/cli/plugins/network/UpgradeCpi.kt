package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestCommand
import picocli.CommandLine.Command
import picocli.CommandLine.ExitCode
import picocli.CommandLine.Option
import java.io.File

// TODO only for dynamic?
@Command(
    name = "upgrade-cpi",
    description = ["Upgrade the CPI used in a network"], // TODO add more description
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
        description = ["Location of the network configuration file."], // TODO add more description
        required = true,
    )
    lateinit var networkConfigFile: File

    override fun run() {
        super.run()
        upgradeCpi()
    }

    private fun upgradeCpi() {
        // Require input files exist
        require(cpiFile.isFile) { "CPI file '$cpiFile' not found." }
        require(networkConfigFile.isFile) { "Network configuration file '$cpiFile' not found." }

        // Require that CPI is a valid CPI file, output the CPI attributes (name, version, etc.)
        // Parse network configuration file. Require that it is successfully parsed.

        // Input Validation:

        // 1. Require dynamic network (network config array includes mgmNode)
        // 1.5 Ignore Notary nodes from the config file !!!
        // 2. Use mgmNode's X500Name and cpi name to query MGM VNode holdingId
        // 3. Lookup members known to MGM holdingId
        // 4. Validate that all members from the config file are present in the response:
        //      - based on X500 name
        //      - NOT based on CPI name, which might be different to what is defined in the config file
        // TODO what should we do with the members' CPI name defined in the config file?

        // 5. Validate that all members' CPI information is different from the new CPI file's attributes
        // 6. For every target member, we can use groupId and X500 name to infer holdingId
        // 7. Verify that VNodes with the holdingId exist
        // 8. Verify that VNodes _don't use BYOD feature_

        // 9. Upload the new CPI file and get checksum

        // Once all requirements are met, we can loop through each target member and perform the upgrade

        TODO("Not implemented yet")
    }
}
