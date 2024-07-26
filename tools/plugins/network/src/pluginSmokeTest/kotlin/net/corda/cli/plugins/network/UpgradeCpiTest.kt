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

class UpgradeCpiTest {
    companion object {
        private const val CPB_FILE = "test-cordapp-5.3.0.0-SNAPSHOT-package.cpb"

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

        private val cpiName = "MyCorDapp-${UUID.randomUUID()}"
        private val signingOptions = OnboardMember().createDefaultSingingOptions().asSigningOptionsSdk
        private val cpiUploader = CpiUploader(restClient)

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

            initialCpiFile = createCpiFile("1.0", cpiName)
            val cpiChecksum = uploadCpi(initialCpiFile)

            onboardMember(memberNameAlice, cpiChecksum)
            onboardMember(memberNameBob, cpiChecksum)
            onboardMember(notaryName, cpiChecksum, notary = true)

            assertThat(getGroupMembers().size).isEqualTo(4)
        }

        private fun createCpiFile(cpiVersion: String, cpiName: String): File {
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
                groupPolicy = mgmGroupPolicyFile.readText(),
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

    data class MemberNode(val x500Name: MemberX500Name, val cpi: String = "Whatever", val mgmNode: Boolean? = null)

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
            cpiName, // Using the same initial test CPI for the notary
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

    @Test
    fun `only members from the config file are upgraded, the rest in the group are skipped`() {
        // read CPI information of the exiting members
        val existingMembersOtherThanBob = getGroupMembers().filter {
            it.memberContext["corda.name"] != memberNameBob.toString()
        }

        // execute upgrade with only one member from the config file
        val networkConfigFile = createNetworkConfigFile(MemberNode(memberNameBob))
        val newCpiVersion = "111.1"
        val cpiFile = createCpiFile(newCpiVersion, cpiName)

        val (outText, exitCode) = TestUtils.captureStdErr {
            executeUpgradeCpi(
                "--cpi-file=${cpiFile.absolutePath}",
                "--network-config-file=${networkConfigFile.absolutePath}",
            )
        }
        assertThat(exitCode).isZero()
        assertThat(outText).isEmpty()

        // read CPI information of the upgraded member and verify CPI information
        val membersWithNewCpiVersion = getGroupMembers().filter {
            it.memberContext["corda.cpi.version"] == newCpiVersion && it.memberContext["corda.cpi.name"] == cpiName
        }
        assertThat(membersWithNewCpiVersion).hasSize(1)
        assertThat(membersWithNewCpiVersion.first().memberContext["corda.name"]).isEqualTo(memberNameBob.toString())

        // verify that the rest of the members were not upgraded
        val skippedMembers = getGroupMembers().filterNot {
            it.memberContext["corda.cpi.version"] == newCpiVersion
        }
        assertThat(skippedMembers).isEqualTo(existingMembersOtherThanBob)
    }

    @Test
    fun `members are upgraded, MGM and Notary are skipped`() {
        // read CPI information of the existing Notary, MGM, and all members
        val existingMembers = getGroupMembers()

        // execute upgrade of both Alice and Bob members
        val networkConfigFile = createDefaultNetworkConfigFile()
        val newCpiVersion = "200.0"
        val newCpiFile = createCpiFile(newCpiVersion, cpiName)

        val (outText, exitCode) = TestUtils.captureStdErr {
            executeUpgradeCpi(
                "--cpi-file=${newCpiFile.absolutePath}",
                "--network-config-file=${networkConfigFile.absolutePath}",
            )
        }
        assertThat(exitCode).isZero()
        assertThat(outText).isEmpty()

        // verify that members are upgraded
        val membersWithNewCpiVersion = getGroupMembers().filter {
            it.memberContext["corda.cpi.version"] == newCpiVersion && it.memberContext["corda.cpi.name"] == cpiName
        }
        assertThat(membersWithNewCpiVersion).hasSize(2)
        val upgradedCordaNames = membersWithNewCpiVersion.map { it.memberContext["corda.name"] }
        assertThat(upgradedCordaNames).containsExactlyInAnyOrder(memberNameAlice.toString(), memberNameBob.toString())

        // but MGM and Notary remain intact
        val skippedMembers = getGroupMembers().filterNot {
            it.memberContext["corda.cpi.version"] == newCpiVersion
        }
        assertThat(skippedMembers).containsExactlyInAnyOrder(
            existingMembers.first { it.memberContext["corda.name"] == mgmName.toString() },
            existingMembers.first { it.memberContext["corda.name"] == notaryName.toString() },
        )
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
        assertThat(errText).contains("CPI file '$cpiFilePath' does not exist or is not readable.")
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
            .contains("Network configuration file '$networkConfigFilePath' does not exist or is not readable.")
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
        assertThat(errText).contains("Error reading CPI file: Error reading manifest")
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
    fun `MGM node is not found in the network config file`() {
        val noMgmConfigFile = createNetworkConfigFile(mgm = false)
        val (errText, exitCode) = TestUtils.captureStdErr {
            executeUpgradeCpi(
                "--cpi-file=${initialCpiFile.absolutePath}",
                "--network-config-file=${noMgmConfigFile.absolutePath}",
            )
        }
        assertThat(exitCode).isNotZero()
        assertThat(errText).contains("Network configuration file does not contain MGM node")
    }

    @Test
    fun `more that one MGM node in the network config file`() {
        val twoMgmConfigFile = createNetworkConfigFile(
            MemberNode(memberNameAlice),
            MemberNode(memberNameBob, mgmNode = true),
            mgm = true,
        )
        val (errText, exitCode) = TestUtils.captureStdErr {
            executeUpgradeCpi(
                "--cpi-file=${initialCpiFile.absolutePath}",
                "--network-config-file=${twoMgmConfigFile.absolutePath}",
            )
        }
        assertThat(exitCode).isNotZero()
        assertThat(errText).contains("Invalid number of MGM nodes defined, can only specify one.")
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
    fun `MGM is not found in Corda`() {
        val aliceMgmConfigFile = createNetworkConfigFile(
            MemberNode(memberNameAlice, "MGM", mgmNode = true),
            MemberNode(memberNameBob),
            mgm = false,
        )
        val (errText, exitCode) = TestUtils.captureStdErr {
            executeUpgradeCpi(
                "--cpi-file=${initialCpiFile.absolutePath}",
                "--network-config-file=${aliceMgmConfigFile.absolutePath}",
            )
        }
        assertThat(exitCode).isNotZero()
        assertThat(errText).contains("MGM virtual node with X.500 name '$memberNameAlice' and CPI name 'MGM' not found among existing virtual nodes.")
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
            .contains("The following members from the network configuration file are not present in the network:")
            .contains(memberNameCharlie.toString())
            .doesNotContain(
                memberNameAlice.toString(),
                memberNameBob.toString(),
                notaryName.toString(),
            )
    }

    @Test
    fun `all members from the config file are missing in Corda`() {
        val dale = getMemberName("Dale")
        val eddie = getMemberName("Eddie")
        val frank = getMemberName("Frank")

        val allUnknownMembersConfigFile = createNetworkConfigFile(
            MemberNode(dale),
            MemberNode(eddie),
            MemberNode(frank),
        )
        val (errText, exitCode) = TestUtils.captureStdErr {
            executeUpgradeCpi(
                "--cpi-file=${initialCpiFile.absolutePath}",
                "--network-config-file=${allUnknownMembersConfigFile.absolutePath}",
            )
        }

        assertThat(exitCode).isNotZero()
        assertThat(errText)
            .contains("The following members from the network configuration file are not present in the network:")
            .contains(dale.toString(), eddie.toString(), frank.toString())
    }

    @Test
    fun `some members CPI information is the same as the target CPI file's attributes`() {
        val existingMembers = getGroupMembers()

        // prepare CPI file with the same attributes as of one of the members
        val bobMemberContext = existingMembers.first { it.memberContext["corda.name"] == memberNameBob.toString() }.memberContext
        val bobCpiName = bobMemberContext["corda.cpi.name"]!!
        val bobCpiVersion = bobMemberContext["corda.cpi.version"]!!
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
            .contains("One or more members from the network configuration file have the same CPI information as the target CPI file:")
            .contains(memberNameBob.toString())
            .doesNotContain(
                memberNameAlice.toString(),
                notaryName.toString(),
                mgmName.toString(),
            )
    }

    @Test
    fun `some of the target VNodes use BYOD feature`() {
        // TODO figure out how to setup and test this
        //  !!! looks like there's no way to determine whether a VNode uses that feature or not
    }

    @Test
    fun `new CPI has different name from what members use currently`() {
        // read CPI information of the existing Notary, MGM, and all members
        val existingMembers = getGroupMembers()

        // execute upgrade of both Alice and Bob members
        val networkConfigFile = createDefaultNetworkConfigFile()
        val newCpiVersion = "222.0"
        val newCpiFile = createCpiFile(newCpiVersion, "NewCpiName-${UUID.randomUUID()}")

        val (outText, exitCode) = TestUtils.captureStdErr {
            executeUpgradeCpi(
                "--cpi-file=${newCpiFile.absolutePath}",
                "--network-config-file=${networkConfigFile.absolutePath}",
            )
        }
        assertThat(exitCode).isNotZero()
        assertThat(outText).isEmpty()

        // verify that members are not upgraded
        val membersAfterFailedUpgrade = getGroupMembers()
        assertThat(membersAfterFailedUpgrade).isEqualTo(existingMembers)
    }
}
