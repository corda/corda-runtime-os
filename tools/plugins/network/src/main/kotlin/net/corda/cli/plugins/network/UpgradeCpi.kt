package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestCommand
import net.corda.restclient.CordaRestClient
import net.corda.sdk.network.config.NetworkConfig
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import java.net.URI
import java.nio.file.Files

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

    private val restClient: CordaRestClient by lazy {
        CordaRestClient.createHttpClient(
            baseUrl = URI.create(targetUrl),
            username = username,
            password = password,
            insecure = insecure
        )
    }

    override fun run() {
        super.run()

//        val exitCode = verifyAndPrintError {
        upgradeCpi()
//        }

//        exitProcess(exitCode)
    }

    private fun upgradeCpi() {
        // Require input files exist and are readable
        require(Files.isReadable(cpiFile.toPath())) { "CPI file '$cpiFile' does not exist or is not readable." }
        require(Files.isReadable(networkConfigFile.toPath())) {
            "Network configuration file '$networkConfigFile' does not exist or is not readable."
        }

        // Require that CPI is a valid CPI file, output the CPI attributes (name, version, etc.)
        // !!! Can't do that properly, will verify on upload base on response from Corda

        // Parse network configuration file. Require that it is successfully parsed.
        val networkConfig = NetworkConfig(networkConfigFile.absolutePath)

        // Input Validation:

        // 1. Require dynamic network (network config array includes mgmNode)
        require(networkConfig.mgmNodeIsPresentInNetworkDefinition) { "Network configuration file does not contain MGM node." }

        // 1.5 Ignore Notary nodes from the config file !!!
        val configMembers = networkConfig.vNodes.filter { it.serviceX500Name == null && !it.mgmNode.toBoolean() }
        require(configMembers.isNotEmpty()) { "Network configuration file does not contain any members to upgrade." }
        // TODO: test this
        require(configMembers.all { it.x500Name != null }) { "Network configuration file contains members without X.500 name." }

        val configMgmVNode = networkConfig.getMgmNode()

        // 2. Use mgmNode's X500Name and cpi name to query MGM VNode holdingId
        val mgmCpiName = configMgmVNode?.cpi ?: error("MGM node in the network configuration file does not have a CPI name defined.")
        val mgmX500Name = configMgmVNode.x500Name ?: error("MGM node in the network configuration file does not have an X.500 name defined.")

        val existingVNodes = restClient.virtualNodeClient.getVirtualnode().virtualNodes
        val mgmHoldingId = existingVNodes.firstOrNull {
            it.holdingIdentity.x500Name == mgmX500Name && it.cpiIdentifier.cpiName == mgmCpiName
        }?.holdingIdentity?.shortHash ?: error(
            "MGM virtual node with X.500 name '$mgmX500Name' and CPI name '$mgmCpiName' not found among existing virtual nodes."
        )

        // 3. Lookup members known to MGM holdingId
        val existingMembers = restClient.memberLookupClient.getMembersHoldingidentityshorthash(mgmHoldingId).members

        // 4. Validate that all members from the config file are present in the response:
        //      - based on X500 name
        //      - NOT based on CPI name, which might be different to what is defined in the config file
        // TODO what should we do with the members' CPI name defined in the config file? - Ignore it

        // TODO: convert x500 names to MemberX500Name objects for comparison
        val unknownConfigMembers = configMembers.filter { configMember ->
            configMember.x500Name !in existingMembers.map { it.memberContext["corda.name"] }
        }
        require(unknownConfigMembers.isEmpty()) {
            "The following members from the network configuration file are not present in the network: " +
                unknownConfigMembers.joinToString(", ") { "'${it.x500Name!!}'" }
        }

        val targetExistingMembers = existingMembers.filter { existingMember ->
            existingMember.memberContext["corda.name"] in configMembers.map { it.x500Name }
        }

        // 5. Validate that all members' CPI information is different from the new CPI file's attributes
        // 6. For every target member, we can use groupId and X500 name to infer holdingId
        // 7. Verify that VNodes with the holdingId exist
        // 8. Verify that VNodes _don't use BYOD feature_

        // 9. Verify that target members list is not empty

        // 9.5 Save members' current CPI information
        // 10. Upload the new CPI file and get checksum
        //   -- if failed, report the error

        // Once all requirements are met, we can loop through each target member and perform the upgrade
        // -- if unable to put VNode back to ACTIVE (failed schemas check),
        //  report what's wrong and suggest that user either
        //   - completes the upgrade process manually (generates the SQL and executes against the DB)
        //   - or reverts the upgrade (replaces the CPI file with the old one - get info from the saved members' CPI information)
        //  and then puts the VNode back to ACTIVE manually
    }
}
