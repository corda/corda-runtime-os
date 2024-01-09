package net.corda.p2p.tests

import net.corda.e2etest.utilities.ClusterInfo
import net.corda.e2etest.utilities.P2PEndpointInfo
import net.corda.e2etest.utilities.RestEndpointInfo
import net.corda.e2etest.utilities.SimpleResponse
import net.corda.e2etest.utilities.lookup
import net.corda.e2etest.utilities.onboardMember
import net.corda.e2etest.utilities.onboardMgm
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.rest.annotations.RestApiVersion
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
import kotlin.concurrent.thread

// To run from the terminal use:
//   CLUSTERS_COUNT=1 MEMBER_COUNT=5 ./gradlew :applications:tools:p2p-test:app-simulator:test --tests="*LargeNetworkTest*"
// Or from the script
//  CLUSTERS_COUNT=1 MEMBER_COUNT=5 ./applications/tools/p2p-test/app-simulator/scripts/large-network-testing/runScenario.sh
@EnabledIfEnvironmentVariable(named = "CLUSTERS_COUNT", matches = "\\d+")
@EnabledIfEnvironmentVariable(named = "MEMBER_COUNT", matches = "\\d+")
class LargeNetworkTest {
    private companion object {
        private val userName by lazy {
            System.getProperty("user.name")
        }
        private val clock = Clock.systemDefaultZone()
        private val formatter = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault())
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
    private val mgmName by lazy {
        MemberX500Name("MGM", testName, "London", "GB")
    }
    private data class OnboardedMember(
        val holdingId: String,
        val memberX500Name: MemberX500Name,
    )
    private class PortForward(clusterName: String) : AutoCloseable {
        val port = ServerSocket(0).use {
            it.localPort
        }
        private val process = ProcessBuilder().command(
            "kubectl",
            "port-forward",
            "--namespace",
            clusterName,
            "deployment/corda-rest-worker",
            "$port:8888",
        ).start().also { process ->
            Runtime.getRuntime().addShutdownHook(
                thread(false) {
                    process.destroy()
                },
            )
            // When port forward is running it will output two lines (one for ipv4 and one for ipv6).
            // For example:
            //   Forwarding from 127.0.0.1:4321 -> 8888
            //   Forwarding from [::1]:4321 -> 8888
            // We want to capture those two line to know that the port forward is ready.
            val reader = process.inputStream.bufferedReader()
            reader.readLine()
            reader.readLine()
        }

        private val created = clock.instant()

        fun isValid(): Boolean {
            return if (!process.isAlive) {
                println("forward process had died")
                false
            } else if (Duration.between(created, clock.instant()) > Duration.ofMinutes(30)) {
                println("forward process had been alive for too long")
                false
            } else {
                true
            }
        }

        override fun close() {
            process.destroy()
            process.waitFor()
        }
    }
    private class Cluster(override val id: String) : ClusterInfo() {
        override val restApiVersion = RestApiVersion.C5_1
        override val p2p: P2PEndpointInfo by lazy {
            P2PEndpointInfo(
                host = "corda-p2p-gateway-worker.$id",
                port = 8080,
                protocol = "1",
            )
        }

        private var portForward: PortForward? = null

        override val rest: RestEndpointInfo
            get() {
                val port = portForward.let {
                    if (it?.isValid() != true) {
                        portForward?.close()
                        val forward = PortForward(id)
                        portForward = forward
                        forward.port
                    } else {
                        it.port
                    }
                }

                return RestEndpointInfo(
                    host = "localhost",
                    user = "admin",
                    password = "admin",
                    port = port,
                )
            }

        val onboardedMembers = mutableListOf<OnboardedMember>()
    }

    @BeforeEach
    fun tearDown() {
        logDuration("cleaning") {
            val tearDownScript = File(scriptDir, "tearDown.sh")
            val tearDown = ProcessBuilder(
                listOf(tearDownScript.absolutePath) + clusters.map { it.id },
            )
                .inheritIO()
                .start()
            if (tearDown.waitFor() != 0) {
                throw CordaRuntimeException("Failed to tear down clusters")
            }
        }
    }

    @AfterEach
    fun captureLogs() {
        val buildDir = File(scriptDir, "build")
        val outputDir = File(buildDir, "logs")
        clusters.map {
            it.id
        }.map { clusterName ->
            val clusterOutputDir = File(outputDir, clusterName)
            clusterOutputDir.deleteRecursively()
            clusterOutputDir.mkdirs()
            val getPods = ProcessBuilder(
                "kubectl",
                "get",
                "pod",
                "-n",
                clusterName,
            ).start()
            getPods.waitFor()
            val pods = getPods.inputStream
                .reader()
                .readLines()
                .map {
                    it.split(" ").filter { it.isNotBlank() }
                }.filter {
                    it[0] != "NAME"
                }.filter {
                    it[2] == "Running"
                }.map {
                    it[0]
                }
            pods.forEach { podName ->
                logDuration("saving logs of $podName in $clusterName") {
                    val logFile = File(clusterOutputDir, "$podName.log")
                    val getLogs = ProcessBuilder(
                        "kubectl",
                        "logs",
                        "-n",
                        clusterName,
                        podName,
                        "--all-containers",
                        "--ignore-errors",
                        "--timestamps",
                    ).redirectOutput(logFile)
                        .start()
                    getLogs.waitFor()
                }
            }
        }
        println("Logs saved into $outputDir")
        tearDown()
    }

    @Test
    fun `test large network`() {
        deployClusters()
        deployMembers()
    }

    private fun deployMembers() {
        for (i in 1..memberCount) {
            clusters.forEach { cluster ->
                logDuration("onboarding member $i into ${cluster.id} out of $memberCount") {
                    val memberX500Name = MemberX500Name("Member-$i-${cluster.id}", testName, "London", "GB")
                    val holdingId = cluster.onboardMember(
                        null,
                        testName,
                        groupPolicyFactory,
                        memberX500Name.toString(),
                    ).holdingId
                    cluster.onboardedMembers.add(
                        OnboardedMember(
                            holdingId = holdingId,
                            memberX500Name = memberX500Name,
                        ),
                    )
                }
            }
            if (i % 5 == 0) {
                eventually(
                    waitBefore = Duration.ofMinutes(0),
                    duration = Duration.ofMinutes(10),
                    waitBetween = Duration.ofSeconds(10),
                    retryAllExceptions = true,
                ) {
                    logDuration("validate network") {
                        validateNetwork()
                    }
                }
            }
        }
    }

    private fun validateNetwork() {
        val start = clock.instant()
        val expectedNodes = clusters.flatMap { cluster ->
            cluster.onboardedMembers
        }.map {
            it.memberX500Name
        } + mgmName
        println("expecting = ${expectedNodes.size} members")
        clusters.forEach { cluster ->
            cluster.onboardedMembers
                .shuffled()
                .take(20)
                .forEach { member ->
                    assertThat(
                        cluster
                            .lookup(member.holdingId)
                            .toMembersNames(),
                    ).containsAll(expectedNodes)
                }
        }
        println("All seemed valid - took ${Duration.between(start, clock.instant()).toMillis() / 1000.0} seconds")
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
    private val groupPolicyFactory by lazy {
        mgmCluster.onboardMgm(mgmName.toString()).getGroupPolicyFactory()
    }

    private fun deployClusters() {
        val prereqsEksFile = File(
            File(scriptDir, "large-network-testing"),
            "prereqs-eks-large-network.yaml",
        )
        val cordaEksFile = File(scriptDir, "corda-eks-large.yaml")
        val deployScript = File(scriptDir, "deploy.sh")
        println("deploy = $deployScript")
        println("prereqsEksFile = $prereqsEksFile")
        println("cordaEksFile = $cordaEksFile")
        logDuration("deploy clusters") {
            val deploy = ProcessBuilder(
                listOf(deployScript.absolutePath) + clusters.map { it.id },
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

    private fun Duration.format(): String {
        return "${this.toMillis() / 1000.0} seconds"
    }
    private fun logDuration(what: String, block: () -> Unit) {
        val start = clock.instant()
        println("${formatter.format(start)}: Starting $what.")
        block()
        println("Done $what in ${Duration.between(start, clock.instant()).format()}")
    }
}
