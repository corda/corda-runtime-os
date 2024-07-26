package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.network.utils.PrintUtils.verifyAndPrintError
import net.corda.cli.plugins.network.utils.requireFileExists
import net.corda.crypto.core.ShortHash
import net.corda.data.flow.output.FlowStates
import net.corda.libs.virtualnode.common.constant.VirtualNodeStateTransitions
import net.corda.rest.ResponseCode
import net.corda.restclient.CordaRestClient
import net.corda.sdk.data.Checksum
import net.corda.sdk.data.RequestId
import net.corda.sdk.network.UpgradeVirtualNode
import net.corda.sdk.network.VirtualNode
import net.corda.sdk.network.config.NetworkConfig
import net.corda.sdk.network.config.VNode
import net.corda.sdk.packaging.CpiAttributes
import net.corda.sdk.packaging.CpiUploader
import net.corda.sdk.packaging.CpiV2Creator
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

    private val restClient: CordaRestClient by lazy {
        CordaRestClient.createHttpClient(
            baseUrl = URI.create(targetUrl),
            username = username,
            password = password,
            insecure = insecure
        )
    }

    private val upgradeVirtualNode by lazy { UpgradeVirtualNode(restClient) }

    override fun call(): Int {
        super.run()

        val exitCode = verifyAndPrintError {
            upgradeCpi()
        }

        return exitCode
    }

    private fun upgradeCpi(): Int {
        requireFileExists(cpiFile)
        requireFileExists(networkConfigFile)

        readAndValidateCpiMetadata()

        val networkConfig = NetworkConfig(networkConfigFile.absolutePath)

        // FROM SDK
        val upgradeHoldingIds = upgradeVirtualNode.getUpgradeHoldingIds(
            networkConfig,
            CpiAttributes(cpiName, cpiVersion)
        )

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

        val vNodeUpgradeErrors = upgradeHoldingIds.associateWith {
            runCatching {
                upgradeVirtualNode(it, cpiChecksum)
            }.exceptionOrNull()
        }.filterValues { it != null }

        if (vNodeUpgradeErrors.isNotEmpty()) {
            System.err.println("Upgrade failed for some virtual nodes:")
            vNodeUpgradeErrors.forEach { (holdingId, error) ->
                System.err.println("HoldingId: $holdingId, error: ${error?.message}")
            }
            return CommandLine.ExitCode.SOFTWARE
        }

        return CommandLine.ExitCode.OK
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
