package net.corda.cli.plugins.network

import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.network.utils.PrintUtils.verifyAndPrintError
import net.corda.cli.plugins.network.utils.requireFileExists
import net.corda.crypto.core.ShortHash
import net.corda.rest.ResponseCode
import net.corda.restclient.CordaRestClient
import net.corda.sdk.data.Checksum
import net.corda.sdk.data.RequestId
import net.corda.sdk.network.VirtualNodeUpgrade
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
    description = [
        "Upgrade the CPI used by the member parties in the dynamic network.",
        "Network configuration file is required to determine the members to upgrade.",
        "MGM node information from the configuration file is used to determine the membership group, " +
            "then member nodes information is used to determine the virtual nodes to upgrade in the membership group."
    ],
    mixinStandardHelpOptions = true,
)
class UpgradeCpi : Callable<Int>, RestCommand() {
    companion object {
        const val BYODB_WARNING_MESSAGE = "If virtual nodes use bring-your-own-database feature (BYODB), " +
            "you might need to update the virtual nodes' vault databases manually. " +
            "Please refer to 'Upgrading a CPI' section of Corda 5 documentation for details."
    }

    @Option(
        names = ["--cpi-file"],
        description = ["Location of the CPI file to upgrade the members to."],
        required = true,
    )
    lateinit var cpiFile: File

    @Option(
        names = ["--network-config-file"],
        description = [
            "Location of the network configuration file.",
            "The file uses the same format as Corda Runtime Gradle Plugin."
        ],
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

    private val virtualNodeUpgrade by lazy { VirtualNodeUpgrade(restClient) }
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
        println("Upgrading CPI '$cpiName' to version '$cpiVersion'")

        val networkConfig = NetworkConfig(networkConfigFile.absolutePath)

        val (mgmHoldingId, membersBeforeUpgrade) = virtualNodeUpgrade.getMembersWithHoldingId(
            networkConfig,
            CpiAttributes(cpiName, cpiVersion)
        )
        println("Using MGM with holdingId: $mgmHoldingId")

        val cpiChecksum = uploadCpiAndGetChecksum()
        println("Uploaded CPI checksum: ${cpiChecksum.value}")

        val vNodeUpgradeErrors = membersBeforeUpgrade.associateWith {
            print("Upgrading virtual node ${it.holdingId} with name '${it.partyName}'... ")
            runCatching {
                virtualNodeUpgrade.upgradeVirtualNode(it, cpiChecksum)
            }
                .onSuccess { print("done\n") }.onFailure { print("failed\n") }
                .exceptionOrNull()
        }.filterValues { it != null }

        if (vNodeUpgradeErrors.isNotEmpty()) {
            reportUpgradeErrors(vNodeUpgradeErrors)
            return CommandLine.ExitCode.SOFTWARE
        }

        print("Waiting for members info to be updated... ")
        virtualNodeUpgrade.waitUntilMembersInfoIsUpdated(mgmHoldingId, membersBeforeUpgrade)
        print("done\n")

        return CommandLine.ExitCode.OK
    }

    private fun reportUpgradeErrors(vNodeUpgradeErrors: Map<VirtualNodeUpgrade.MemberContext, Throwable?>) {
        System.err.println("Upgrade failed for some virtual nodes.\n$BYODB_WARNING_MESSAGE")
        vNodeUpgradeErrors.forEach { (member, error) ->
            System.err.println("Virtual node ${member.holdingId} with name '${member.partyName}', error: ${error?.message}")
        }
    }


    private fun uploadCpiAndGetChecksum(): Checksum {
        val uploadRequestId = cpiUploader.uploadCPI(cpiFile).id // If upload fails, this throws an exception
        return cpiUploader.cpiChecksum(
            RequestId(uploadRequestId),
            escapeOnResponses = listOf(ResponseCode.CONFLICT, ResponseCode.BAD_REQUEST) // TODO do we still need this?
        )
    }

    private fun readAndValidateCpiMetadata(): Pair<String, String> {
        val (cpiAttributes, cpiEntries) = getCpiAttributesAndEntries()

        val cpiName = cpiAttributes[CpiV2Creator.CPI_NAME_ATTRIBUTE_NAME]?.toString()
            ?: error("CPI file does not contain a name attribute.")
        val cpiVersion = cpiAttributes[CpiV2Creator.CPI_VERSION_ATTRIBUTE_NAME]?.toString()
            ?: error("CPI file does not contain a version attribute.")

        require(cpiEntries.isNotEmpty()) { "CPI file does not contain any entries." }
        require(cpiEntries.any { it.key == CpiV2Creator.META_INF_GROUP_POLICY_JSON }) {
            "CPI file does not contain a ${CpiV2Creator.META_INF_GROUP_POLICY_JSON} entry."
        }
        return cpiName to cpiVersion
    }

    private fun getCpiAttributesAndEntries() = try {
        JarInputStream(Files.newInputStream(cpiFile.toPath(), StandardOpenOption.READ)).use {
            val manifest = it.manifest ?: error("Error reading manifest from CPI file.")
            manifest.mainAttributes to manifest.entries
        }
    } catch (e: Exception) {
        error("Error reading CPI file: ${e.message}")
    }
}
