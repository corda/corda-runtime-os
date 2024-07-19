package net.corda.cli.plugins.network

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.membership.lib.MemberInfoExtension
import net.corda.restclient.CordaRestClient
import net.corda.restclient.generated.models.RestMemberInfo
import net.corda.sdk.data.Checksum
import net.corda.sdk.data.RequestId
import net.corda.sdk.packaging.CpiAttributes
import net.corda.sdk.packaging.CpiUploader
import net.corda.sdk.packaging.CpiV2Creator
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import picocli.CommandLine
import java.io.File
import java.nio.file.Path
import java.util.UUID

class UpgradeCpiTest {
    companion object {
        // TODO do everything in beforeEach
        private const val CPB_FILE = "test-cordapp.cpb"

        private val targetUrl = "--target=${DEFAULT_CLUSTER.rest.uri}"
        private val user = "--user=${DEFAULT_CLUSTER.rest.user}"
        private val password = "--password=${DEFAULT_CLUSTER.rest.password}"
        private const val INSECURE = "--insecure=true"

        private fun getMemberName(name: String = "Member") = MemberX500Name.parse("O=$name-${UUID.randomUUID()}, L=London, C=GB")

        private val mgmName = getMemberName("MGM")
        private val memberNameAlice = getMemberName("Alice")
        private val memberNameBob = getMemberName("Bob")
        private val memberNameCharlie = getMemberName("Charlie")
        private val notaryName = getMemberName("Notary")
        private const val NOTARY_SERVICE_NAME = "O=NotaryService, L=London, C=GB"

        private val cpbLocation: String = this::class.java.classLoader.getResource(CPB_FILE)!!.path
        private val defaultGroupPolicyLocation: String = "${System.getProperty("user.home")}/.corda/gp/groupPolicy.json"
        private val restClient = CordaRestClient.createHttpClient(
            baseUrl = DEFAULT_CLUSTER.rest.uri,
            username = DEFAULT_CLUSTER.rest.user,
            password = DEFAULT_CLUSTER.rest.password,
            insecure = true,
        )

        private lateinit var groupId: String
        private lateinit var mgmHoldingId: String

        private val initialCpiName = "MyCorDapp-${UUID.randomUUID()}"
        private val signingOptions = OnboardMember().createDefaultSingingOptions().asSigningOptionsSdk
        private val cpiUploader = CpiUploader(restClient)
        private lateinit var mgmMemberInfo: RestMemberInfo
        private lateinit var mgmCpiName: String

        private val mapper = ObjectMapper()

        @BeforeAll
        @JvmStatic
        fun setup() {
            onboardMgm()

            // TODO is it needed?
            groupId = mapper.readTree(File(defaultGroupPolicyLocation).readText()).get("groupId").asText()

            val cpiChecksumAlice = uploadCpi(createCpiFile("1.0", initialCpiName))
            // Use different CPI metadata for Bob (but same corDapp)
            val cpiChecksumBob = uploadCpi(createCpiFile("2.0", "FOOBAR-${UUID.randomUUID()}"))

            onboardMember(memberNameAlice, cpiChecksumAlice)
            onboardMember(memberNameBob, cpiChecksumBob)

            onboardMember(notaryName, cpiChecksumAlice, notary = true)

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
                groupPolicy = File(defaultGroupPolicyLocation).readText(),
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
            CommandLine(OnboardMgm()).execute(mgmName.toString(), targetUrl, user, password, INSECURE)
            mgmHoldingId = getHoldingIdForMember(mgmName)
            assertThat(getGroupMembersNames()).containsExactly(mgmName.toString())
            mgmMemberInfo = getGroupMembers().first { it.memberContext["corda.name"] == mgmName.toString() }
            mgmCpiName = mgmMemberInfo.memberContext["corda.cpi.name"]!!
        }

        private fun getGroupMembers(): List<RestMemberInfo> =
            restClient.memberLookupClient.getMembersHoldingidentityshorthash(mgmHoldingId).members

        private fun getGroupMembersNames(): List<String> = getGroupMembers().map { it.memberContext["corda.name"]!! }

        private fun getHoldingIdForMember(memberName: MemberX500Name): String = // TODO rework to use groupId?
            restClient.virtualNodeClient.getVirtualnode().virtualNodes
                .first { it.holdingIdentity.x500Name == memberName.toString() }
                .holdingIdentity.shortHash

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
            // TODO assert that notary is onboarded correctly?
        }
    }

    // TODO Move to a separate file
    data class MemberNode(val x500Name: MemberX500Name, val cpi: String = "Whatever", val mgmNode: Boolean? = null)

    private fun createNetworkConfigFile(): File = createNetworkConfigFile(
        MemberNode(memberNameAlice),
        MemberNode(memberNameBob),
    )

    private fun createNetworkConfigFile(vararg members: MemberNode, mgm: Boolean = true, notary: Boolean = true): File {
        val networkConfig = mapper.createArrayNode()

        val memberNodes = members.map { member ->
            mapper.createObjectNode().apply {
                put("x500Name", member.x500Name.toString())
                put("cpi", member.cpi)
                member.mgmNode?.let { put("mgmNode", it) }
            }
        }
        networkConfig.addAll(memberNodes)

        if (mgm) {
            networkConfig.add(mapper.createObjectNode().apply {
                put("x500Name", mgmName.toString())
                put("cpi", mgmCpiName)
                put("mgmNode", true)
            })
        }
        if (notary) {
            networkConfig.add(mapper.createObjectNode().apply {
                put("x500Name", notaryName.toString())
                put("cpi", initialCpiName) // Using the same initial test CPI for the notary
                put("serviceX500Name", NOTARY_SERVICE_NAME)
                put("flowProtocolName", "com.r3.corda.notary.plugin.nonvalidating")
                put("backchainRequired", true)
            })
        }

        val networkConfigFile = File.createTempFile("network-config-", ".json").also {
            it.deleteOnExit()
            it.writeText(mapper.writeValueAsString(networkConfig))
        }
        return networkConfigFile
    }


    private fun executeUpgradeCpi(vararg args: String): Int {
        val restArgs = listOf(targetUrl, user, password, INSECURE)
        return CommandLine(UpgradeCpi()).execute(*(args.toList() + restArgs).toTypedArray())
    }

    @Test
    fun `feature not implemented throws an error`() {
        val newCpiName = "MyCorDapp-${UUID.randomUUID()}"
        val cpiFile = createCpiFile(cpiVersion = "2.0", cpiName = newCpiName)
        val networkConfigFile = createNetworkConfigFile()
        assertThatThrownBy {
            executeUpgradeCpi(
                "--cpi-file=${cpiFile.absolutePath}",
                "--network-config-file=${networkConfigFile.absolutePath}",
            )
        }.isInstanceOf(NotImplementedError::class.java)
    }

    // Negative tests - failed validations

    @Test
    fun `missing required options cpi-file and network-config-file`() {
        val errText = TestUtils.captureStdErr { executeUpgradeCpi() }
        assertThat(errText).contains(
            "Missing required options",
            "--cpi-file",
            "--network-config-file",
        )
    }

    @Test
    fun `invalid CPI file`() {

    }

    @Test
    fun `network config file can't be parsed`() {

    }

    @Test
    fun `MGM node is not found in the network config file`() {

    }

    @Test
    fun `more that one MGM node in the network config file`() {

    }

    @Test
    fun `members are not found in the network config file`() {

    }

    @Test
    fun `MGM node is not found in Corda`() {

    }

    @Test
    fun `MGM doesn't hold any members`() {

    }

    @Test
    fun `(negative) some members from the config file are missing in Corda`() {

    }

    @Test
    fun `(or rather) validate that all members from the config file are present in Corda`() {

    }

    @Test
    fun `some members CPI information is the same as the target CPI file's attributes`() {
        // read CPI information of the existing members
        // prepare CPI file with the same attributes as of one of the members


    }

    @Test
    fun `some of the target VNodes use BYOD feature`() {

    }


    @Test
    fun `only members from the config file are upgraded, the rest in the group are skipped`() {

    }

    @Test
    fun `members are upgraded, MGM and Notary are skipped`() {

    }

}
