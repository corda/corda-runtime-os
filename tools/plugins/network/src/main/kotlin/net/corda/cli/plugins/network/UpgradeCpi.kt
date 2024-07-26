package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.network.utils.PrintUtils.verifyAndPrintError
import net.corda.crypto.core.ShortHash
import net.corda.data.flow.output.FlowStates
import net.corda.libs.virtualnode.common.constant.VirtualNodeStateTransitions
import net.corda.rest.ResponseCode
import net.corda.restclient.CordaRestClient
import net.corda.sdk.data.Checksum
import net.corda.sdk.data.RequestId
import net.corda.sdk.network.MemberLookup
import net.corda.sdk.network.VirtualNode
import net.corda.sdk.network.config.NetworkConfig
import net.corda.sdk.network.config.VNode
import net.corda.sdk.packaging.CpiUploader
import net.corda.sdk.packaging.CpiV2Creator
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.concurrent.Callable
import java.util.jar.JarInputStream

// TODO only for dynamic?
@Command(
    name = "upgrade-cpi",
    description = ["Upgrade the CPI used in a network"], // TODO add more description
    mixinStandardHelpOptions = true,
)
class UpgradeCpi : Callable<Int>, RestCommand() {
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

    private lateinit var cpiName: String
    private lateinit var cpiVersion: String
    private lateinit var networkConfig: NetworkConfig
    private lateinit var configMembers: List<VNode>
    private lateinit var mgmCpiName: String
    private lateinit var mgmX500Name: String

    private val restClient: CordaRestClient by lazy {
        CordaRestClient.createHttpClient(
            baseUrl = URI.create(targetUrl),
            username = username,
            password = password,
            insecure = insecure
        )
    }

    override fun call(): Int {
        super.run()

        val exitCode = verifyAndPrintError {
            upgradeCpi()
        }

        return exitCode
    }

    private fun upgradeCpi(): Int {
        val virtualNode = VirtualNode(restClient)
        val memberLookup = MemberLookup(restClient)

        validateAndReadInputFiles()

        val existingVNodes = virtualNode.getAllVirtualNodes().virtualNodes
        val mgmHoldingId = existingVNodes.firstOrNull {
            it.holdingIdentity.x500Name == mgmX500Name && it.cpiIdentifier.cpiName == mgmCpiName
        }?.holdingIdentity?.shortHash ?: error(
            "MGM virtual node with X.500 name '$mgmX500Name' and CPI name '$mgmCpiName' not found among existing virtual nodes."
        )

        println("MGM holdingId: $mgmHoldingId")

        // 3. Lookup members known to MGM holdingId
        val existingMembers = memberLookup.lookupMember(ShortHash.of(mgmHoldingId), status = emptyList()).members

        // 4. Validate that all members from the config file are present in the response:
        //      - based on X500 name
        //      - NOT based on CPI name, which might be different to what is defined in the config file
        // TODO what should we do with the members' CPI name defined in the config file?

        // TODO  - check this!!! PUT Upgrade CPI errors with 400 if CPI name is different:
//        {
//            "title": "Upgrade CPI must have the same name as the current CPI.",
//            "status": 400,
//            "details": {}
//        }

        // TODO convert x500 names to MemberX500Name objects for comparison
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

        // 9. Verify that target members list is not empty
        // Double-checking, this should not be the case after the prior validations
        require(targetExistingMembers.isNotEmpty()) { "No target members found." }

        // 5. Validate that all members' CPI information is different from the new CPI file's attributes
        // -- that is done on upload, check for 409. If any member has the same CPI name and version, this means such CPI
        //    already exists in the given Corda instance, and the upload will fail with 409.
        // TODO: check this before upload by analysing CPI file metadata - to save time.

        // 6. For every target member, we can use groupId and X500 name to infer holdingId
        val targetHoldingIds = targetExistingMembers.map {
            HoldingIdentity(MemberX500Name.parse(it.memberContext["corda.name"]!!), it.memberContext["corda.groupId"]!!)
                .shortHash.toString()
        }

        // 7. Verify that VNodes with the holdingId exist
        val existingVNodesHoldingIds: List<String> = existingVNodes.map { it.holdingIdentity.shortHash }

        // Double-checking, this should not be the case after the prior validations
        require(existingVNodesHoldingIds.containsAll(targetHoldingIds)) {
            "The following target members' holdingIds are not present among existing virtual nodes: " +
                    targetHoldingIds.joinToString(", ") // TODO report together with the members' X500 names
        }

        // 8. Verify that VNodes _don't use BYOD feature_ - no way to do that!!

        // 9.5 Save members' current CPI information
        // -- saved in targetExistingMembers

        // 10. Upload the new CPI file and get checksum
        //   -- if failed, report the error
        val cpiUploader = CpiUploader(restClient)

        val uploadRequestId = cpiUploader.uploadCPI(cpiFile).id // If upload fails, this throws an exception
        val cpiChecksum = cpiUploader.cpiChecksum(
            RequestId(uploadRequestId),
            escapeOnResponses = listOf(ResponseCode.CONFLICT, ResponseCode.BAD_REQUEST)
        )
        println("Uploaded CPI checksum: $cpiChecksum")

        // Once all requirements are met, we can loop through each target member and perform the upgrade
        // -- if unable to put VNode back to ACTIVE (failed schemas check),
        //  report what's wrong and suggest that user either
        //   - completes the upgrade process manually (generates the SQL and executes against the DB)
        //   - or reverts the upgrade (replaces the CPI file with the old one - get info from the saved members' CPI information)
        //  and then puts the VNode back to ACTIVE manually

        val errors = targetHoldingIds
            .map { ShortHash.of(it) }
            .associateWith{
                runCatching { upgradeVirtualNode(it, cpiChecksum) }
                    .onFailure { e -> println("Failed to upgrade virtual node with holdingId $it: ${e.message}") }
                    .exceptionOrNull()
            }
            .filterValues { it != null }

        if (errors.isNotEmpty()) {
            System.err.println("Upgrade failed for some virtual nodes:")
            errors.forEach { (holdingId, error) ->
                System.err.println("HoldingId: $holdingId, error: ${error?.message}")
            }
            return CommandLine.ExitCode.SOFTWARE
        }

        return CommandLine.ExitCode.OK
    }

    private fun validateFileIsReadable(file: File, name: String) {
        require(Files.isReadable(file.toPath())) { "$name file '$file' does not exist or is not readable." }
    }

    private fun validateAndReadInputFiles() {
        validateFileIsReadable(cpiFile, "CPI")
        validateFileIsReadable(networkConfigFile, "Network configuration")

        readAndValidateCpiMetadata()

        networkConfig = NetworkConfig(networkConfigFile.absolutePath)

        require(networkConfig.mgmNodeIsPresentInNetworkDefinition) { "Network configuration file does not contain MGM node." }

        configMembers = networkConfig.vNodes.filter { it.serviceX500Name == null && !it.mgmNode.toBoolean() }
        require(configMembers.isNotEmpty()) { "Network configuration file does not contain any members to upgrade." }
        require(configMembers.all { it.x500Name != null }) { "Network configuration file contains members without X.500 name." }

        val configMgmVNode: VNode = networkConfig.getMgmNode()!!

        mgmCpiName = configMgmVNode.cpi
            ?: error("MGM node in the network configuration file does not have a CPI name defined.")
        mgmX500Name = configMgmVNode.x500Name
            ?: error("MGM node in the network configuration file does not have an X.500 name defined.")
    }

    private fun readAndValidateCpiMetadata() {
        val (cpiAttributes, cpiEntries) = cpiAttributesAndEntries

        cpiName = cpiAttributes[CpiV2Creator.CPI_NAME_ATTRIBUTE_NAME]?.toString() ?: error("CPI file does not contain a name attribute.")
        cpiVersion = cpiAttributes[CpiV2Creator.CPI_VERSION_ATTRIBUTE_NAME]?.toString() ?: error("CPI file does not contain a version attribute.")

        require(cpiEntries.isNotEmpty()) { "CPI file does not contain any entries." }
        require(cpiEntries.any { it.key == CpiV2Creator.META_INF_GROUP_POLICY_JSON }) {
            "CPI file does not contain a ${CpiV2Creator.META_INF_GROUP_POLICY_JSON} entry."
        }
    }

    // TODO move most of the logic to SDK
    private fun upgradeVirtualNode(holdingId: ShortHash, cpiChecksum: Checksum) {
        val virtualNode = VirtualNode(restClient)

        // 1. Set the state of the virtual node to MAINTENANCE
        virtualNode.updateState(holdingId, VirtualNodeStateTransitions.MAINTENANCE)

        // 2. Ensure no flows are running (that is, all flows have either “COMPLETED”, “FAILED” or “KILLED” status)
        // TODO move to companion object
        val terminatedFlowStates = listOf(FlowStates.COMPLETED, FlowStates.FAILED, FlowStates.KILLED).map { it.name }

        // TODO wrap in SDK with a retry
        val runningFlows = restClient.flowManagementClient.getFlowHoldingidentityshorthash(holdingId.value)
            .flowStatusResponses.filter { it.flowStatus !in terminatedFlowStates }

        require(runningFlows.isEmpty()) {
            "There are running flows on the virtual node with holdingId $holdingId:\n" +
            runningFlows.joinToString("\n")
        }

        // 3. For the BYODB - report to the user VNode HoldingIds, CPI checksum, and/or the API path they need to hit to get the SQL

        // 4. Send the checksum of the CPI to upgrade to using the PUT method
        virtualNode.upgradeCpiAndWaitForSuccess(holdingId, cpiChecksum)

        // 5. TODO The endpoint triggers the member to re-register with the MGM - check if we need to wait for anything
        //   -- probably not, as we have waited for the upgrade to complete with SUCCEEDED status

        // 6. Set the state of the virtual node back to ACTIVE
        virtualNode.updateState(holdingId, VirtualNodeStateTransitions.ACTIVE)
        //   6.5 If failed, throw and collect the error
        println("Virtual node with holdingId $holdingId upgraded successfully")
    }

    private val cpiAttributesAndEntries by lazy {
        try {
            JarInputStream(Files.newInputStream(cpiFile.toPath(), StandardOpenOption.READ)).use {
                val manifest = it.manifest ?: error("Error reading manifest.")
                manifest.mainAttributes to manifest.entries
            }
        } catch (e: Exception) {
            error("Error reading CPI file: ${e.message}")
        }
    }
}
