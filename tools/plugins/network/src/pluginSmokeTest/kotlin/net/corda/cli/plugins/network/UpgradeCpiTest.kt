package net.corda.cli.plugins.network

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.cli.plugins.network.utils.PrintUtils
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.membership.lib.MemberInfoExtension
import net.corda.restclient.CordaRestClient
import net.corda.restclient.generated.models.RestMemberInfo
import net.corda.sdk.data.Checksum
import net.corda.sdk.data.RequestId
import net.corda.sdk.network.config.VNode
import net.corda.sdk.packaging.CpiAttributes
import net.corda.sdk.packaging.CpiUploader
import net.corda.sdk.packaging.CpiV2Creator
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import picocli.CommandLine
import java.io.File
import java.nio.file.Path
import java.util.*
import net.corda.rbac.schema.RbacKeys.UUID_REGEX
import net.corda.restclient.generated.models.StartFlowParameters
import net.corda.restclient.generated.models.VirtualNodeInfo
import net.corda.sdk.rest.RestClientUtils.executeWithRetry
import org.assertj.core.api.Assertions.assertThatCode
import kotlin.time.Duration.Companion.seconds

class UpgradeCpiTest {
    companion object {
        private const val CPB_FILE = "test-cordapp.cpb"

        private val targetUrl = "--target=${DEFAULT_CLUSTER.rest.uri}"
        private val user = "--user=${DEFAULT_CLUSTER.rest.user}"
        private val password = "--password=${DEFAULT_CLUSTER.rest.password}"
        private const val INSECURE = "--insecure=true"
        private val restClient = CordaRestClient.createHttpClient(
            baseUrl = DEFAULT_CLUSTER.rest.uri,
            username = DEFAULT_CLUSTER.rest.user,
            password = DEFAULT_CLUSTER.rest.password,
            insecure = true,
        )

        private fun getMemberName(name: String = "Member") = MemberX500Name.parse("O=$name-${UUID.randomUUID()}, L=London, C=GB")

        private val mgmName = getMemberName("MGM")
        private val memberNameAlice = getMemberName("Alice")
        private val memberNameBob = getMemberName("Bob")
        private val memberNameCharlie = getMemberName("Charlie")
        private val notaryName = getMemberName("Notary")
        private const val NOTARY_SERVICE_NAME = "O=NotaryService, L=London, C=GB"

        private val cpbLocation: String = this::class.java.classLoader.getResource(CPB_FILE)!!.path
        private val mgmGroupPolicyFile: File = File.createTempFile("group-policy-", ".json").also {
            it.deleteOnExit()
            it.delete()
        }

        private val commonCpiName = "MyCorDapp-${UUID.randomUUID()}"
        private const val INITIAL_CPI_VERSION = "1.0"
        private val cpiUploader = CpiUploader(restClient)

        private val signingOptions = OnboardMember().createDefaultSingingOptions().asSigningOptionsSdk

        private lateinit var initialCpiFile: File
        private lateinit var mgmMemberInfo: RestMemberInfo
        private lateinit var mgmCpiName: String

        val mapper = ObjectMapper().apply {
            setSerializationInclusion(JsonInclude.Include.NON_NULL)
        }

        @BeforeAll
        @JvmStatic
        fun setup() {
            onboardMgm()

            initialCpiFile = createCpiFile(INITIAL_CPI_VERSION, commonCpiName)
            val cpiChecksum = uploadCpi(initialCpiFile)

            onboardMember(memberNameAlice, cpiChecksum)
            onboardMember(memberNameBob, cpiChecksum)
            onboardMember(notaryName, cpiChecksum, notary = true)

            assertThat(getGroupMembers().size).isEqualTo(4)
        }

        private fun createCpiFile(cpiVersion: String, cpiName: String, groupPolicyFile: File = mgmGroupPolicyFile): File {
            val cpiFile = File.createTempFile("test-cpi-v$cpiVersion-", ".cpi").also {
                it.deleteOnExit()
                it.delete()
            }
            CpiV2Creator.createCpi(
                cpbPath = Path.of(cpbLocation),
                outputFilePath = cpiFile.toPath(),
                cpiAttributes = CpiAttributes(
                    cpiName = cpiName,
                    cpiVersion = cpiVersion,
                    cpiUpgrade = false,
                ),
                groupPolicy = groupPolicyFile.readText(),
                signingOptions = signingOptions,
            )
            require(cpiFile.isFile) { "Failed to create CPI file $cpiFile" }
            return cpiFile
        }

        private fun uploadCpi(cpiFile: File): Checksum {
            val cpiUploadStatus = cpiUploader.uploadCPI(cpiFile)
            return cpiUploader.cpiChecksum(RequestId(cpiUploadStatus.id))
        }

        private fun onboardMgm() {
            CommandLine(OnboardMgm()).execute(
                mgmName.toString(),
                "--save-group-policy-as=${mgmGroupPolicyFile.absolutePath}",
                targetUrl,
                user,
                password,
                INSECURE
            )
            assertThat(getGroupMembersNames()).containsExactly(mgmName.toString())
            mgmMemberInfo = getGroupMembers().first { it.memberContext["corda.name"] == mgmName.toString() }
            mgmCpiName = mgmMemberInfo.memberContext["corda.cpi.name"]!!
        }

        private fun getGroupMembers(): List<RestMemberInfo> =
            restClient.memberLookupClient.getMembersHoldingidentityshorthash(mgmHoldingId).members

        private fun getGroupMembersNames(): List<String> = getGroupMembers().map { it.memberContext["corda.name"]!! }

        private fun getGroupId(): String = mgmMemberInfo.memberContext["corda.groupId"]!!
        private fun getGroupVirtualNodes(): List<VirtualNodeInfo> =
            restClient.virtualNodeClient.getVirtualnode().virtualNodes.filter { it.holdingIdentity.groupId == getGroupId() }

        private val mgmHoldingId: String by lazy {
            restClient.virtualNodeClient.getVirtualnode().virtualNodes
                .first { it.holdingIdentity.x500Name == mgmName.toString() }
                .holdingIdentity.shortHash
        }

        private fun onboardMember(memberName: MemberX500Name, cpiChecksum: Checksum, notary: Boolean = false) {
            val commonArgs = listOf(
                memberName.toString(),
                "--cpi-hash=${cpiChecksum.value}",
                targetUrl,
                user,
                password,
                INSECURE,
                "--wait",
            )

            val notaryArgs = listOf(
                "--role=NOTARY",
                "--set=${MemberInfoExtension.NOTARY_SERVICE_NAME}=$NOTARY_SERVICE_NAME",
            )

            val args = if (notary) commonArgs + notaryArgs else commonArgs

            CommandLine(OnboardMember()).execute(*args.toTypedArray())
            assertThat(getGroupMembersNames()).contains(memberName.toString())
        }
    }

    data class MemberNode(val x500Name: MemberX500Name, val cpi: String = commonCpiName, val mgmNode: Boolean? = null)

    private fun createDefaultNetworkConfigFile(): File = createNetworkConfigFile(
        MemberNode(memberNameAlice),
        MemberNode(memberNameBob),
    )

    private fun createNetworkConfigFile(
        vararg members: MemberNode,
        mgm: Boolean = true,
        notary: Boolean = true,
    ): File {
        val mgmNode = if (mgm) listOf(VNode(
            mgmName.toString(),
            mgmCpiName,
            mgmNode = true.toString(),
        )) else emptyList()

        val notaryNode = if (notary) listOf(VNode(
            notaryName.toString(),
            commonCpiName, // Using the same initial test CPI for the notary
            NOTARY_SERVICE_NAME,
            "com.r3.corda.notary.plugin.nonvalidating",
            backchainRequired = true.toString(),
        )) else emptyList()

        val memberNodes = members.map { member ->
            VNode(
                member.x500Name.toString(),
                member.cpi,
                mgmNode = member.mgmNode?.toString(),
            )
        }

        val networkConfigFile = File.createTempFile("network-config-", ".json").also {
            it.deleteOnExit()
            it.writeText(
                mapper
                    .writer(PrintUtils.prettyPrintWriter)
                    .writeValueAsString(memberNodes + mgmNode + notaryNode)
            )
        }

        return networkConfigFile
    }

    private fun executeUpgradeCpi(vararg args: String): Int {
        val restArgs = listOf(targetUrl, user, password, INSECURE)
        return CommandLine(UpgradeCpi()).execute(*(args.toList() + restArgs).toTypedArray())
    }

    private fun assertVirtualNodeCanRunFlows(holdingId: String) {
        val requestId = UUID.randomUUID().toString()
        val flowParameters = StartFlowParameters(
            clientRequestId = requestId,
            flowClassName = "com.r3.corda.testing.smoketests.virtualnode.ReturnAStringFlow",
            requestBody = "{}",
        )
        assertThatCode {
            executeWithRetry(
                operationName = "Start flow",
                waitDuration = 60.seconds,
            ) { restClient.flowManagementClient.postFlowHoldingidentityshorthash(holdingId, flowParameters) }

            val result = executeWithRetry(
                operationName = "Get flow result",
                waitDuration = 10.seconds,
            ) {
                restClient.flowManagementClient.getFlowHoldingidentityshorthashClientrequestid(holdingId, requestId).also {
                    if (it.flowStatus != "COMPLETED") throw IllegalStateException("Flow is not completed")
                }
            }
            assertThat(result.flowResult).isEqualTo("original-cpi")
        }.doesNotThrowAnyException()
    }

    private fun getMemberVirtualNodeInfo(memberName: MemberX500Name): VirtualNodeInfo {
        return getGroupVirtualNodes().first { it.holdingIdentity.x500Name == memberName.toString() }
    }

    private fun assertAliceAndBobCanRunFlows() {
        val aliceHoldingId = getMemberVirtualNodeInfo(memberNameAlice).holdingIdentity.shortHash
        val bobHoldingId = getMemberVirtualNodeInfo(memberNameBob).holdingIdentity.shortHash

        assertVirtualNodeCanRunFlows(aliceHoldingId)
        assertVirtualNodeCanRunFlows(bobHoldingId)
    }

    @Test
    fun `only members from the config file are upgraded, the rest in the group are skipped`() {
        val existingVNodesOtherThanBob = getGroupVirtualNodes().filter {
            it.holdingIdentity.x500Name != memberNameBob.toString()
        }

        val networkConfigFile = createNetworkConfigFile(MemberNode(memberNameBob))
        val newCpiVersion = "111.1"
        val cpiFile = createCpiFile(newCpiVersion, commonCpiName)

        val (outText, exitCode) = TestUtils.captureStdErr {
            executeUpgradeCpi(
                "--cpi-file=${cpiFile.absolutePath}",
                "--network-config-file=${networkConfigFile.absolutePath}",
            )
        }
        assertThat(exitCode).isZero()
        assertThat(outText).isEmpty()

        val memberVNodesWithNewVersion = waitAndGetVNodesWithNewVersion(newCpiVersion, 1)
        assertThat(memberVNodesWithNewVersion.first().holdingIdentity.x500Name).isEqualTo(memberNameBob.toString())

        val skippedVNodes = getGroupVirtualNodes().filterNot {
            it.cpiIdentifier.cpiVersion == newCpiVersion
        }
        assertThat(skippedVNodes).isEqualTo(existingVNodesOtherThanBob)

        assertAliceAndBobCanRunFlows()
    }

    private fun waitAndGetVNodesWithNewVersion(newCpiVersion: String, vNodesCount: Int): List<VirtualNodeInfo> {
        return executeWithRetry(
            operationName = "CpiIdentifier has updated after CPI upgrade for $vNodesCount nodes",
        ) {
            getGroupVirtualNodes().filter {
                it.cpiIdentifier.cpiVersion == newCpiVersion && it.cpiIdentifier.cpiName == commonCpiName
            }.also {
                if (it.size != vNodesCount) throw IllegalStateException("Expecting $vNodesCount members to be upgraded")
            }
        }
    }

    @Test
    fun `members are upgraded, MGM and Notary are skipped`() {
        // read CPI information of the existing Notary, MGM, and all members
        val existingVNodes = getGroupVirtualNodes()

        // execute upgrade of both Alice and Bob members
        val networkConfigFile = createDefaultNetworkConfigFile()
        val newCpiVersion = "200.0"
        val newCpiFile = createCpiFile(newCpiVersion, commonCpiName)

        val (outText, exitCode) = TestUtils.captureStdErr {
            executeUpgradeCpi(
                "--cpi-file=${newCpiFile.absolutePath}",
                "--network-config-file=${networkConfigFile.absolutePath}",
            )
        }
        assertThat(exitCode).isZero()
        assertThat(outText).isEmpty()

        // verify that members are upgraded
        val memberVNodesWithNewVersion = waitAndGetVNodesWithNewVersion(newCpiVersion, 2)

        val upgradedCordaNames = memberVNodesWithNewVersion.map { it.holdingIdentity.x500Name }
        assertThat(upgradedCordaNames).containsExactlyInAnyOrder(
            memberNameAlice.toString(),
            memberNameBob.toString(),
        )

        // but MGM and Notary remain intact
        val skippedVNodes = getGroupVirtualNodes().filterNot {
            it.cpiIdentifier.cpiVersion == newCpiVersion
        }
        assertThat(skippedVNodes).containsExactlyInAnyOrder(
            existingVNodes.first { it.holdingIdentity.x500Name == mgmName.toString() },
            existingVNodes.first { it.holdingIdentity.x500Name == notaryName.toString() },
        )

        assertAliceAndBobCanRunFlows()
    }

    // Negative tests
    @Test
    fun `missing required options cpi-file and network-config-file`() {
        val (errText, exitCode) = TestUtils.captureStdErr { executeUpgradeCpi() }
        assertThat(exitCode).isNotZero()
        assertThat(errText).contains(
            "Missing required options",
            "--cpi-file",
            "--network-config-file",
        )
    }

    @Test
    fun `CPI file is not readable`() {
        val networkConfigFilePath = createDefaultNetworkConfigFile().absolutePath
        val cpiFilePath = File("non-existing-${UUID.randomUUID()}.cpi").absolutePath

        val (errText, exitCode) = TestUtils.captureStdErr {
            executeUpgradeCpi(
                "--cpi-file=$cpiFilePath",
                "--network-config-file=$networkConfigFilePath",
            )
        }
        assertThat(exitCode).isNotZero()
        assertThat(errText).contains("File \"$cpiFilePath\" does not exist or is not readable")
    }

    @Test
    fun `network config file is not readable`() {
        val networkConfigFilePath = File("non-existing-${UUID.randomUUID()}.json").absolutePath
        val cpiFilePath = initialCpiFile.absolutePath

        val (errText, exitCode) = TestUtils.captureStdErr {
            executeUpgradeCpi(
                "--cpi-file=$cpiFilePath",
                "--network-config-file=$networkConfigFilePath",
            )
        }
        assertThat(exitCode).isNotZero()
        assertThat(errText)
            .contains("File \"$networkConfigFilePath\" does not exist or is not readable")
    }

    @Test
    fun `CPI is not a valid corda package`() {
        val networkConfigFile = createDefaultNetworkConfigFile()
        val invalidCpiFile = File.createTempFile("invalid-cpi-", ".cpi").also {
            it.deleteOnExit()
            it.writeText("This is not a valid CPI file")
        }
        val (errText, exitCode) = TestUtils.captureStdErr {
            executeUpgradeCpi(
                "--cpi-file=${invalidCpiFile.absolutePath}",
                "--network-config-file=${networkConfigFile.absolutePath}",
            )
        }
        assertThat(exitCode).isNotZero()
        assertThat(errText).contains("Error reading CPI file")
    }

    @Test
    fun `network config file can't be parsed`() {
        val invalidNetworkConfigFile = File.createTempFile("invalid-network-config-", ".json").also {
            it.deleteOnExit()
            it.writeText("This is not a valid JSON file")
        }
        val (errText, exitCode) = TestUtils.captureStdErr {
            executeUpgradeCpi(
                "--cpi-file=${initialCpiFile.absolutePath}", // Use valid CPI file
                "--network-config-file=${invalidNetworkConfigFile.absolutePath}",
            )
        }
        assertThat(exitCode).isNotZero()
        assertThat(errText).contains("Failed to read network configuration file")
    }

    @Test
    fun `members are not found in the network config file`() {
        val noMembersConfigFile = createNetworkConfigFile()
        val (errText, exitCode) = TestUtils.captureStdErr {
            executeUpgradeCpi(
                "--cpi-file=${initialCpiFile.absolutePath}",
                "--network-config-file=${noMembersConfigFile.absolutePath}",
            )
        }
        assertThat(exitCode).isNotZero()
        assertThat(errText).contains("Network configuration file does not contain any members to upgrade")
    }

    @Test
    fun `some members from the config file are missing in Corda`() {
        val missingMemberConfigFile = createNetworkConfigFile(
            MemberNode(memberNameAlice),
            MemberNode(memberNameBob),
            MemberNode(memberNameCharlie),
        )
        val (errText, exitCode) = TestUtils.captureStdErr {
            executeUpgradeCpi(
                "--cpi-file=${initialCpiFile.absolutePath}",
                "--network-config-file=${missingMemberConfigFile.absolutePath}",
            )
        }
        assertThat(exitCode).isNotZero()
        assertThat(errText)
            .contains("Failed to find following members from the network configuration file among existing virtual nodes with target group")
            .contains(memberNameCharlie.toString())
            .doesNotContain(
                memberNameAlice.toString(),
                memberNameBob.toString(),
                notaryName.toString(),
            )

        assertAliceAndBobCanRunFlows()
    }

    @Test
    fun `all members from the config file are missing in Corda`() {
        val dale = getMemberName("Dale")
        val eddie = getMemberName("Eddie")

        val allUnknownMembersConfigFile = createNetworkConfigFile(
            MemberNode(dale),
            MemberNode(eddie),
        )
        val (errText, exitCode) = TestUtils.captureStdErr {
            executeUpgradeCpi(
                "--cpi-file=${initialCpiFile.absolutePath}",
                "--network-config-file=${allUnknownMembersConfigFile.absolutePath}",
            )
        }

        assertThat(exitCode).isNotZero()
        assertThat(errText)
            .contains("Failed to find following members from the network configuration file among existing virtual nodes with target group")
            .contains(dale.toString(), eddie.toString())

        assertAliceAndBobCanRunFlows()
    }

    @Test
    fun `some members CPI version is the same as the target CPI file version`() {
        val existingVNodes = getGroupVirtualNodes()

        // prepare CPI file with the same attributes as of one of the members
        val bobVNode = existingVNodes.first { it.holdingIdentity.x500Name == memberNameBob.toString() }
        val bobCpiName = bobVNode.cpiIdentifier.cpiName
        val bobCpiVersion = bobVNode.cpiIdentifier.cpiVersion
        val cpiFile = createCpiFile(cpiVersion = bobCpiVersion, cpiName = bobCpiName)

        val networkConfigFile = createDefaultNetworkConfigFile()
        val (errText, exitCode) = TestUtils.captureStdErr {
            executeUpgradeCpi(
                "--cpi-file=${cpiFile.absolutePath}",
                "--network-config-file=${networkConfigFile.absolutePath}",
            )
        }
        assertThat(exitCode).isNotZero()
        assertThat(errText)
            .contains("One or more target virtual nodes have the same CPI version as the target CPI file:")
            .contains(memberNameBob.toString())
            .doesNotContain(
                notaryName.toString(),
                mgmName.toString(),
            )

        assertAliceAndBobCanRunFlows()
    }

    @Test
    fun `new CPI has different name from what members use currently`() {
        // read CPI information of the existing Notary, MGM, and all members
        val existingVNodes = getGroupVirtualNodes()

        val networkConfigFile = createDefaultNetworkConfigFile()
        val newCpiVersion = "222.0"
        val newCpiName = "NewCpiName-${UUID.randomUUID()}"
        val newCpiFile = createCpiFile(newCpiVersion, newCpiName)

        val (outText, exitCode) = TestUtils.captureStdErr {
            executeUpgradeCpi(
                "--cpi-file=${newCpiFile.absolutePath}",
                "--network-config-file=${networkConfigFile.absolutePath}",
            )
        }
        assertThat(exitCode).isNotZero()
        assertThat(outText).contains(
            "Network configuration file contains members with CPI name which is different from the target CPI name '$newCpiName'"
        )

        // verify that members are not upgraded
        val vNodesAfterUpgrade = getGroupVirtualNodes()
        assertThat(vNodesAfterUpgrade).isEqualTo(existingVNodes)

        assertAliceAndBobCanRunFlows()
    }

    @Test
    fun `new CPI has different groupId in GroupPolicy than the members currently have`() {
        val invalidGroupId = UUID.randomUUID()
        val invalidGroupPolicy = mgmGroupPolicyFile.readText().replace(
            Regex("\"groupId\" : \"$UUID_REGEX\""), "\"groupId\" : \"$invalidGroupId\""
        )
        val invalidGroupPolicyFile = File.createTempFile("GroupPolicy-", ".json").also {
            it.deleteOnExit()
            it.writeText(invalidGroupPolicy)
        }

        val networkConfigFile = createDefaultNetworkConfigFile()
        val newCpiVersion = "333.0"
        val newCpiFile = createCpiFile(newCpiVersion, commonCpiName, invalidGroupPolicyFile)

        val (outText, exitCode) = TestUtils.captureStdErr {
            executeUpgradeCpi(
                "--cpi-file=${newCpiFile.absolutePath}",
                "--network-config-file=${networkConfigFile.absolutePath}",
            )
        }
        assertThat(exitCode).isNotZero()
        assertThat(outText)
            .contains("Failed to find following members from the network configuration file among existing virtual nodes with target group $invalidGroupId")
            .contains(memberNameAlice.toString(), memberNameBob.toString())

        assertAliceAndBobCanRunFlows()
    }
}
