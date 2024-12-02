package net.corda.cli.plugins.network

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.cli.plugins.common.RestCommand
import net.corda.cli.plugins.network.utils.PrintUtils.verifyAndPrintError
import net.corda.cli.plugins.network.utils.requireFileExists
import net.corda.crypto.core.ShortHash
import net.corda.restclient.CordaRestClient
import net.corda.restclient.generated.models.VirtualNodeInfo
import net.corda.sdk.data.Checksum
import net.corda.sdk.data.RequestId
import net.corda.sdk.network.CpiUpgrade
import net.corda.sdk.network.config.NetworkConfig
import net.corda.sdk.packaging.CpiAttributes
import net.corda.sdk.packaging.CpiUploader
import net.corda.sdk.packaging.CpiV2Creator
import picocli.CommandLine.Command
import picocli.CommandLine.ExitCode
import picocli.CommandLine.Option
import java.io.File
import java.net.URI
import java.util.concurrent.Callable
import java.util.jar.JarFile
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds

@Command(
    name = "upgrade-cpi",
    description = [
        "Upgrade the CPI used by the member virtual nodes in the network.",
    ],
    mixinStandardHelpOptions = true,
)
class UpgradeCpi : Callable<Int>, RestCommand() {
    companion object {
        const val BYODB_WARNING_MESSAGE = "If virtual nodes use bring-your-own-database feature (BYODB), " +
            "you might need to update the virtual nodes' vault databases manually. " +
            "Please refer to 'Upgrading a CPI' section of Corda 5 documentation for details."
    }

    data class CpiMetadata(val name: String, val version: String, val groupId: String)

    @Option(
        names = ["--cpi-file"],
        description = [
            "Location of the CPI file to upgrade the members to.",
            "The CPI file is also used to read the membership group ID from the group policy.",
        ],
        required = true,
    )
    lateinit var cpiFile: File

    @Option(
        names = ["--network-config-file"],
        description = [
            "Location of the network configuration file.",
            "The file must be of the same format as Corda Runtime Gradle Plugin.",
            "Members' X.500 names from the file are used to determine the members to upgrade.",
            "Member nodes must have the same CPI name as the target CPI.",
        ],
        required = true,
    )
    lateinit var networkConfigFile: File

    private val cpiUploadWait = max(waitDurationSeconds, 60).seconds
    private val upgradeStepsWait = max(waitDurationSeconds, 30).seconds

    private val restClient: CordaRestClient by lazy {
        CordaRestClient.createHttpClient(
            baseUrl = URI.create(targetUrl),
            username = username,
            password = password,
            insecure = insecure
        )
    }

    private val cpiUpgrade by lazy { CpiUpgrade(restClient) }
    private val cpiUploader by lazy { CpiUploader(restClient) }

    private val cpiJarFile by lazy { JarFile(cpiFile) }
    private val json by lazy { ObjectMapper() }

    override fun call(): Int {
        super.run()

        val exitCode = verifyAndPrintError {
            requireFileExists(cpiFile)
            requireFileExists(networkConfigFile)

            val (cpiName, cpiVersion, groupId) = readAndValidateCpiMetadata()

            val networkConfig = readNetworkConfig(networkConfigFile)

            val targetVirtualNodes = getTargetVirtualNodes(networkConfig, cpiName, cpiVersion, groupId)

            val cpiChecksum = uploadCpiAndGetChecksum()

            val success = upgradeVirtualNodes(targetVirtualNodes, cpiChecksum)

            if (success) ExitCode.OK else ExitCode.SOFTWARE
        }

        return exitCode
    }

    private fun upgradeVirtualNodes(targetVirtualNodes: List<VirtualNodeInfo>, cpiChecksum: Checksum): Boolean {
        val errors = targetVirtualNodes.associateWith {
            val shortHash = ShortHash.of(it.holdingIdentity.shortHash)
            print("Upgrading virtual node $shortHash with name ${it.holdingIdentity.x500Name}... ")

            runCatching {
                cpiUpgrade.upgradeCpiOnVirtualNode(shortHash, cpiChecksum, upgradeStepsWait)
            }
                .onSuccess { print("Done\n") }
                .onFailure { print("Failed\n") }
                .exceptionOrNull()
        }.filterValues { it != null }

        if (errors.isNotEmpty()) {
            reportUpgradeErrors(errors)
            return false
        }

        return true
    }

    private fun getTargetVirtualNodes(
        networkConfig: NetworkConfig,
        cpiName: String,
        cpiVersion: String,
        groupId: String
    ): List<VirtualNodeInfo> {
        println("Looking for member virtual nodes with the group ID $groupId")
        return cpiUpgrade.getTargetVirtualNodes(
            networkConfig,
            CpiAttributes(cpiName, cpiVersion),
            groupId,
            waitDurationSeconds.seconds
        ).also { targetVirtualNodes ->
            println("Found target virtual nodes:")
            targetVirtualNodes.forEach {
                println("- shortHash: ${it.holdingIdentity.shortHash}, name: ${it.holdingIdentity.x500Name}")
            }
        }
    }

    private fun readNetworkConfig(networkConfigFile: File): NetworkConfig =
        NetworkConfig(networkConfigFile.absolutePath).also { networkConfig ->
            println("Member nodes in the configuration file:")

            networkConfig.memberNodes.forEach {
                println("- name: ${it.x500Name}, CPI: ${it.cpi}")
            }
        }

    private fun reportUpgradeErrors(vNodeUpgradeErrors: Map<VirtualNodeInfo, Throwable?>) {
        System.err.println("Upgrade failed for some virtual nodes.\n$BYODB_WARNING_MESSAGE")
        vNodeUpgradeErrors.forEach { (vNode, error) ->
            System.err.println(
                "- shortHash: ${vNode.holdingIdentity.shortHash}, " +
                    "name ${vNode.holdingIdentity.x500Name}: ${error?.message}"
            )
        }
    }

    private fun uploadCpiAndGetChecksum(): Checksum {
        val uploadRequestId = cpiUploader.uploadCPI(cpiFile).id
        return cpiUploader.cpiChecksum(
            RequestId(uploadRequestId),
            cpiUploadWait,
        ).also {
            println("Uploaded CPI checksum: ${it.value}")
        }
    }

    private fun readAndValidateCpiMetadata(): CpiMetadata {
        val (manifest, groupId) = try {
            val manifest = cpiJarFile.manifest ?: error("Error reading manifest from CPI file.")
            val groupPolicyEntry = cpiJarFile.getJarEntry(CpiV2Creator.META_INF_GROUP_POLICY_JSON)
                ?: error("CPI file does not contain a ${CpiV2Creator.META_INF_GROUP_POLICY_JSON} entry.")
            val groupPolicyJson = cpiJarFile.getInputStream(groupPolicyEntry).bufferedReader().use {
                it.readText()
            }
            val groupId = json.readTree(groupPolicyJson)["groupId"].asText()
            manifest to groupId
        } catch (e: Exception) {
            error("Error reading CPI file: ${e.message}")
        }

        val cpiName = manifest.mainAttributes[CpiV2Creator.CPI_NAME_ATTRIBUTE_NAME]?.toString()
            ?: error("CPI file does not contain a name attribute.")
        val cpiVersion = manifest.mainAttributes[CpiV2Creator.CPI_VERSION_ATTRIBUTE_NAME]?.toString()
            ?: error("CPI file does not contain a version attribute.")

        println("Upgrade CPI details:")
        println("- name: $cpiName")
        println("- version: $cpiVersion")
        println("- network group ID: $groupId")

        return CpiMetadata(cpiName, cpiVersion, groupId)
    }
}
