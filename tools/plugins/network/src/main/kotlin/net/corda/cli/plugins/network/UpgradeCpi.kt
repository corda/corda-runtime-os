package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.network.utils.PrintUtils.verifyAndPrintError
import net.corda.cli.plugins.network.utils.requireFileExists
import net.corda.rest.ResponseCode
import net.corda.restclient.CordaRestClient
import net.corda.sdk.data.Checksum
import net.corda.sdk.data.RequestId
import net.corda.sdk.network.UpgradeVirtualNode
import net.corda.sdk.network.config.NetworkConfig
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

    private val restClient: CordaRestClient by lazy {
        CordaRestClient.createHttpClient(
            baseUrl = URI.create(targetUrl),
            username = username,
            password = password,
            insecure = insecure
        )
    }

    private val upgradeVirtualNode by lazy { UpgradeVirtualNode(restClient) }
    private val cpiUploader by lazy { CpiUploader(restClient) }

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

        val (cpiName, cpiVersion) = readAndValidateCpiMetadata()
        println("Upgrading to CPI name '$cpiName' version '$cpiVersion'")

        val networkConfig = NetworkConfig(networkConfigFile.absolutePath)

        val (mgmHoldingId, upgradeHoldingIds) = upgradeVirtualNode.getMembersHoldingIds(
            networkConfig,
            CpiAttributes(cpiName, cpiVersion)
        )
        println("Using MGM with holdingId: $mgmHoldingId")

        val cpiChecksum = uploadCpiAndGetChecksum()
        println("Uploaded CPI checksum: $cpiChecksum")

        // Once all requirements are met, we can loop through each target member and perform the upgrade
        // -- if unable to put VNode back to ACTIVE (failed schemas check),
        //  report what's wrong and suggest that user either
        //   - completes the upgrade process manually (generates the SQL and executes against the DB)
        //   - or reverts the upgrade (replaces the CPI file with the old one - get info from the saved members' CPI information)
        //  and then puts the VNode back to ACTIVE manually

        val vNodeUpgradeErrors = upgradeHoldingIds.associateWith {
            runCatching {
                upgradeVirtualNode.upgradeVirtualNode(it, cpiChecksum)
                println("Virtual node with holdingId $it upgraded successfully")
            }.exceptionOrNull()
        }.filterValues { it != null }

        vNodeUpgradeErrors.map { (holdingId, error) ->
            "Upgrade failed for virtual node with holdingId: $holdingId, error: ${error?.message}"
        }

        if (vNodeUpgradeErrors.isNotEmpty()) {
            System.err.println("Upgrade failed for some virtual nodes:")
            vNodeUpgradeErrors.forEach { (holdingId, error) ->
                System.err.println("HoldingId: $holdingId, error: ${error?.message}")
            }
            return CommandLine.ExitCode.SOFTWARE
        }

        return CommandLine.ExitCode.OK
    }

    private fun uploadCpiAndGetChecksum(): Checksum {
        val uploadRequestId = cpiUploader.uploadCPI(cpiFile).id // If upload fails, this throws an exception
        return cpiUploader.cpiChecksum(
            RequestId(uploadRequestId),
            escapeOnResponses = listOf(ResponseCode.CONFLICT, ResponseCode.BAD_REQUEST) // TODO: do we still need this?
        )
    }

    private fun readAndValidateCpiMetadata(): Pair<String, String> {
        val (cpiAttributes, cpiEntries) = cpiAttributesAndEntries

        val cpiName = cpiAttributes[CpiV2Creator.CPI_NAME_ATTRIBUTE_NAME]?.toString() ?: error("CPI file does not contain a name attribute.")
        val cpiVersion = cpiAttributes[CpiV2Creator.CPI_VERSION_ATTRIBUTE_NAME]?.toString() ?: error("CPI file does not contain a version attribute.")

        require(cpiEntries.isNotEmpty()) { "CPI file does not contain any entries." }
        require(cpiEntries.any { it.key == CpiV2Creator.META_INF_GROUP_POLICY_JSON }) {
            "CPI file does not contain a ${CpiV2Creator.META_INF_GROUP_POLICY_JSON} entry."
        }
        return cpiName to cpiVersion
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
