package net.corda.cli.plugins.network

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.restclient.CordaRestClient
import net.corda.restclient.generated.models.RestMemberInfo
import net.corda.sdk.data.Checksum
import net.corda.sdk.data.RequestId
import net.corda.sdk.packaging.CpiAttributes
import net.corda.sdk.packaging.CpiUploader
import net.corda.sdk.packaging.CpiV2Creator
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import picocli.CommandLine
import java.io.File
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.UUID

class UpgradeCpiTest {
    companion object {
        // TODO do everything in beforeEach
        private const val CPB_FILE = "test-cordapp.cpb"

        private val targetUrl = "--target=${DEFAULT_CLUSTER.rest.uri}"
        private val user = "--user=${DEFAULT_CLUSTER.rest.user}"
        private val password = "--password=${DEFAULT_CLUSTER.rest.password}"
        private const val INSECURE = "--insecure=true"

        private fun getMemberName(name: String = "Member") = MemberX500Name.parse("CN=$name-${UUID.randomUUID()}, L=London, C=GB")

        private val mgmName = getMemberName("MGM")
        private val memberNameAlice = getMemberName("Alice")
        private val memberNameBob = getMemberName("Bob")
        private val memberNameCharlie = getMemberName("Charlie")

        private lateinit var outputStub: OutputStub
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

        private val cpiName = "MyCorDapp-${UUID.randomUUID()}"
        private val signingOptions = OnboardMember().createDefaultSingingOptions().asSigningOptionsSdk
        private val cpiUploader = CpiUploader(restClient)

        @BeforeAll
        @JvmStatic
        fun setup() {
            onboardMgm()

            // TODO is it needed?
            groupId = ObjectMapper().readTree(File(defaultGroupPolicyLocation).readText()).get("groupId").asText()

            val cpiFile = createTestCpiOfVersion("1.0")
            val cpiChecksum = uploadCpi(cpiFile)

            onboardMember(memberNameAlice, cpiChecksum)
            onboardMember(memberNameBob, cpiChecksum)

            assertThat(getGroupMembers().size).isEqualTo(3)
        }

        private fun createTestCpiOfVersion(cpiVersion: String): File {
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
        }

        private fun getGroupMembers(): List<RestMemberInfo> =
            restClient.memberLookupClient.getMembersHoldingidentityshorthash(mgmHoldingId).members

        private fun getGroupMembersNames(): List<String> = getGroupMembers().map { it.memberContext["corda.name"]!! }

        private fun getHoldingIdForMember(memberName: MemberX500Name): String =
            restClient.virtualNodeClient.getVirtualnode().virtualNodes
                .first { it.holdingIdentity.x500Name == memberName.toString() }
                .holdingIdentity.shortHash

        private fun onboardMember(memberName: MemberX500Name, cpiChecksum: Checksum) {
            CommandLine(OnboardMember()).execute(
                memberName.toString(),
                "--cpi-hash=${cpiChecksum.value}",
                "--group-policy-file=$defaultGroupPolicyLocation",
                targetUrl,
                user,
                password,
                INSECURE,
                "--wait",
            )
            assertThat(getGroupMembersNames()).contains(memberName.toString())
        }
    }

    @BeforeEach
    fun beforeEach() {
        outputStub = OutputStub()
    }

    @Test
    fun `foo`() {
        println("Foo")
    }
}
