package net.corda.p2p.tests

import net.corda.e2etest.utilities.ClusterInfo
import net.corda.e2etest.utilities.P2PEndpointInfo
import net.corda.e2etest.utilities.RestEndpointInfo
import net.corda.e2etest.utilities.SimpleResponse
import net.corda.e2etest.utilities.exportGroupPolicy
import net.corda.e2etest.utilities.lookup
import net.corda.e2etest.utilities.onboardMember
import net.corda.e2etest.utilities.onboardMgm
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.test.util.eventually
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.File
import java.net.ServerSocket
import java.time.Clock
import java.time.Duration
import java.util.UUID
import kotlin.concurrent.thread

@EnabledIfEnvironmentVariable(named = "CLUSTERS_COUNT", matches = "\\d+")
@EnabledIfEnvironmentVariable(named = "MEMBER_COUNT", matches = "\\d+")
// To run from the terminal use:
//   CLUSTERS_COUNT=1 MEMBER_COUNT=5 ./gradlew :applications:tools:p2p-test:app-simulator:test --tests="*LargeNetworkTest*"
// Or from the script
//  CLUSTERS_COUNT=1 MEMBER_COUNT=5 ./applications/tools/p2p-test/app-simulator/scripts/large-network-testing/runScenario.sh
class LargeNetworkTest {
    private companion object {
        private val userName by lazy {
            System.getProperty("user.name")
        }
        private val clock = Clock.systemDefaultZone()
    }
    private val scriptDir by lazy {
        File(System.getenv("SCRIPT_DIR"))
    }
    private val clusterCount by lazy {
        System.getenv("CLUSTERS_COUNT").toInt()
    }
    private val memberCount by lazy {
        System.getenv("MEMBER_COUNT").toInt()
    }
    private val clusters by lazy {
        (1..clusterCount).map {
            "$userName-cluster-$it"
        }.map {
            Cluster(it)
        }
    }
    private val testName by lazy {
        UUID.randomUUID().toString()
    }
    private val mgmCluster by lazy {
        clusters.first()
    }
    private data class OnboardedMember(
        val holdingId: String,
        val memberX500Name: MemberX500Name,
    )
    private class Cluster(override val id: String) : ClusterInfo() {
        override val p2p: P2PEndpointInfo by lazy {
            P2PEndpointInfo(
                host = "corda-p2p-gateway-worker.$id",
                port = 8080,
                protocol = "1",
            )
        }
        private val port by lazy {
            ServerSocket(0).use {
                it.localPort
            }.also { port ->
                ProcessBuilder().command(
                    "kubectl",
                    "port-forward",
                    "--namespace",
                    id,
                    "deployment/corda-rest-worker",
                    "$port:8888"
                ).start().also { process ->
                    Runtime.getRuntime().addShutdownHook(
                        thread(false) {
                            process.destroy()
                        }
                    )
                    val reader = process.inputStream.bufferedReader()
                    reader.readLine()
                    reader.readLine()
                }
            }
        }
        override val rest: RestEndpointInfo by lazy {
            RestEndpointInfo(
                host = "localhost",
                user = "admin",
                password = "admin",
                port = port,
            )
        }

        val onboardedMembers = mutableListOf<OnboardedMember>()
    }

    @AfterEach
    @BeforeEach
    fun tearDown() {
        val tearDownScript = File(scriptDir, "tearDown.sh")
        val tearDown = ProcessBuilder(
            listOf(tearDownScript.absolutePath) + clusters.map { it.id }
        )
            .inheritIO()
            .start()
        if (tearDown.waitFor() != 0) {
            throw CordaRuntimeException("Failed to tear down clusters")
        }
    }

    @Test
    fun `test large network`() {
        deployClusters()
        deployMembers()
    }

    private fun deployMembers() {
        (1..memberCount).forEach { index ->
            clusters.forEach { cluster ->
                println("${clock.instant()} Onboarding member $index into ${cluster.id}")
                val memberX500Name = MemberX500Name("Member-$index-${cluster.id}", testName, "London", "GB")
                val holdingId = cluster.onboardMember(
                    null,
                    testName,
                    groupPolicy,
                    memberX500Name.toString(),
                ).holdingId
                cluster.onboardedMembers.add(
                    OnboardedMember(
                        holdingId = holdingId,
                        memberX500Name = memberX500Name,
                    )
                )
            }
            if (index % 5 == 0) {
                println("Validate network")
                eventually(
                    duration = Duration.ofMinutes(10),
                    waitBetween = Duration.ofSeconds(10),
                    retryAllExceptions = true,
                ) {
                    validateNetwork()
                }
            }
        }
    }

    private fun validateNetwork() {
        val expectedNodes = clusters.flatMap { cluster ->
            cluster.onboardedMembers
        }.map {
            it.memberX500Name
        }
        println("expecting = ${expectedNodes.size} members")
        clusters.forEach { cluster ->
            cluster.onboardedMembers.forEach { member ->
                assertThat(
                    cluster.
                    lookup(member.holdingId)
                        .toMembersNames()
                ).containsAll(expectedNodes)
            }
        }
        println("All valid")
    }
    private fun SimpleResponse.toMembersNames(): Collection<MemberX500Name> {
        assertThat(this.code).isEqualTo(200)
        return this.toJson()["members"].mapNotNull { json ->
            json.get("memberContext").get(PARTY_NAME).textValue()
        }
            .map {
                MemberX500Name.parse(it)
            }
    }
    private val groupPolicy by lazy {
        println("Onboarding MGM")
        val mgmName = MemberX500Name("MGM", testName, "London", "GB")
        val mgmInfo = mgmCluster.onboardMgm(mgmName.toString())
        mgmCluster.exportGroupPolicy(mgmInfo.holdingId).also {
            assertThat(it).isNotEmpty.isNotBlank
            println("MGM was onboarded")
        }
    }

    private fun deployClusters() {
        val prereqsEksFile = File(
            File(scriptDir, "large-network-testing"),
            "prereqs-eks-large-network.yaml"
        )
        val cordaEksFile = File(scriptDir, "corda-eks-large.yaml")
        val deployScript = File(scriptDir, "deploy.sh")
        println("deploy = $deployScript")
        println("prereqsEksFile = $prereqsEksFile")
        println("cordaEksFile = $cordaEksFile")
        val deploy = ProcessBuilder(
            listOf(deployScript.absolutePath) + clusters.map { it.id }
        )
            .also {
                it.environment()["PREREQS_EKS_FILE"] = prereqsEksFile.absolutePath
                it.environment()["CORDA_EKS_FILE"] = cordaEksFile.absolutePath
            }
            .inheritIO()
            .start()
        if (deploy.waitFor() != 0) {
            throw CordaRuntimeException("Failed to deploy clusters")
        }
    }
}