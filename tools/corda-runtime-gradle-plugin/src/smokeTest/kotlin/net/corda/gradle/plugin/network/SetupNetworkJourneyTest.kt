package net.corda.gradle.plugin.network

import net.corda.gradle.plugin.CombinedWorkerHelper.restClient
import net.corda.gradle.plugin.CombinedWorkerHelper.startCompose
import net.corda.gradle.plugin.CombinedWorkerHelper.stopCompose
import net.corda.gradle.plugin.SmokeTestBase
import net.corda.gradle.plugin.queries.LIST_CPIS_TASK_NAME
import net.corda.gradle.plugin.queries.LIST_VNODES_TASK_NAME
import net.corda.restclient.generated.models.RestRegistrationRequestStatus.RegistrationStatus.APPROVED
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test

class SetupNetworkJourneyTest : SmokeTestBase() {
    private val vNodeRegisteredMessage = Regex("VNode .+ with shortHash [A-F0-9]+ registered.")
    private val staticCpiNames = listOf("MyCorDapp", "NotaryServer")
    private val dynamicCpiNames = staticCpiNames + "MGM"

    @BeforeEach
    fun startCombinedWorker() {
        startCompose()
    }

    @AfterEach
    fun stopCombinedWorker() {
        stopCompose()
    }

    @Test
    fun setupStaticNetworkVerifyVNodesAndCPIsThenRedeploy() {
        val vNodeSetupResult = executeWithRunner(
            VNODE_SETUP_TASK_NAME,
            "--info", "--stacktrace",
            forwardOutput = true,
            isStaticNetwork = true
        )
        vNodeSetupResult.task(":$VNODE_SETUP_TASK_NAME")!!.assertTaskSucceeded()

        val expectedCommonNames = listOf("Alice", "Bob", "Charlie", "Dave", "NotaryRep1")
        val registeredVNodes = verifyRegisteredVNodesInOutput(vNodeSetupResult.output, expectedCommonNames)

        // List CPIs and verify output
        val actualCpis = runListCPIsTaskAndVerifyOutput(staticCpiNames)
        val myCorDappCpiChecksum = actualCpis["MyCorDapp"]!!

        // List VNodes and verify output
        runListVNodesTaskAndVerifyOutput(staticCpiNames, expectedCommonNames, registeredVNodes.values.toList())

        // Verify startable flows
        val myCorDappFlows = restClient.flowInfoClient.getFlowclassHoldingidentityshorthash(registeredVNodes["Alice"]!!)
        assertThat(myCorDappFlows.flowClassNames).isEmpty()

        verifyNotaryCpks()

        verifyRedeployNetwork(staticCpiNames, isStaticNetwork = true, myCorDappCpiChecksum)
    }

    @RepeatedTest(20)
    fun setupDynamicNetworkVerifyVNodesAndCPIsThenRedeploy() {
        // Create a static network
        val vNodeSetupResult = executeWithRunner(
            VNODE_SETUP_TASK_NAME,
            "--info", "--stacktrace",
            forwardOutput = true,
            isStaticNetwork = false
        )
        vNodeSetupResult.task(":$VNODE_SETUP_TASK_NAME")!!.assertTaskSucceeded()

        val expectedCommonNames = listOf("Alice", "Bob", "Charlie", "Dave", "NotaryRep1", "MGM")
        val registeredVNodes = verifyRegisteredVNodesInOutput(vNodeSetupResult.output, expectedCommonNames)

        // List CPIs and verify output
        val actualCpis = runListCPIsTaskAndVerifyOutput(dynamicCpiNames)
        val myCorDappCpiChecksum = actualCpis["MyCorDapp"]!!

        // List VNodes and verify output
        runListVNodesTaskAndVerifyOutput(dynamicCpiNames, expectedCommonNames, registeredVNodes.values.toList())

        // Verify startable flows
        val myCorDappFlows = restClient.flowInfoClient.getFlowclassHoldingidentityshorthash(registeredVNodes["Alice"]!!)
        assertThat(myCorDappFlows.flowClassNames).isEmpty()

        verifyNotaryCpks()

        val cpiInfo = restClient.cpiClient.getCpi().cpis

        // Verify MGM-related CPI info
        val mgmCpiInfo = cpiInfo.first { it.id.cpiName == "MGM" }
        assertThat(mgmCpiInfo.groupPolicy).contains("net.corda.membership.impl.registration.dynamic.mgm.MGMRegistrationService")
        assertThat(mgmCpiInfo.cpks).isEmpty()

        val memberCpis = cpiInfo - mgmCpiInfo
        assertThat(memberCpis).allSatisfy { assertThat(it.groupPolicy).contains("mgmInfo") }

        // Verify dynamic network members
        val mgmRegistrations = restClient.mgmClient
            .getMgmHoldingidentityshorthashRegistrations(registeredVNodes["MGM"]!!, viewhistoric = true)
        val approvedRegistrationCommonNames = mgmRegistrations
            .filter { it.registrationStatus == APPROVED && it.memberInfoSubmitted.data.containsKey("corda.name") }
            .map {
                it.memberInfoSubmitted.data["corda.name"]!!.split("CN=").last().split(",").first()
            }
        val expectedMemberCommonNames = expectedCommonNames - "MGM"
        assertThat(approvedRegistrationCommonNames).containsExactlyInAnyOrderElementsOf(expectedMemberCommonNames)

        // Verify Notary member details
        val notaryMemberInfo = mgmRegistrations
            .first { it.memberInfoSubmitted.data["corda.cpi.name"] == "NotaryServer" }.memberInfoSubmitted.data
        assertThat(notaryMemberInfo).containsEntry("corda.notary.service.backchain.required", "false")
        assertThat(notaryMemberInfo).containsEntry("corda.notary.service.flow.protocol.name", "com.r3.corda.notary.plugin.nonvalidating")

        verifyRedeployNetwork(dynamicCpiNames, isStaticNetwork = false, myCorDappCpiChecksum)
    }

    private fun verifyRedeployNetwork(expectedCpiNames: List<String>, isStaticNetwork: Boolean, previousMyCorDappCpiChecksum: String) {
        val reDeployNetworkResult = executeWithRunner(
            VNODE_SETUP_TASK_NAME,
            "--info", "--stacktrace",
            forwardOutput = true,
            isStaticNetwork = isStaticNetwork
        )
        // VNodes registration is skipped
        assertThat(reDeployNetworkResult.output).doesNotContainPattern(vNodeRegisteredMessage.pattern)

        val actualCpisAfterReDeploy = runListCPIsTaskAndVerifyOutput(expectedCpiNames)

        // Verify that the CorDapp has been re-deployed
        val newMyCorDappChecksum = actualCpisAfterReDeploy["MyCorDapp"]!!
        assertThat(newMyCorDappChecksum).isNotEqualTo(previousMyCorDappCpiChecksum)
    }

    private fun verifyRegisteredVNodesInOutput(output: String, expectedCommonNames: List<String>): Map<String, String> {
        assertThat(output).containsPattern(vNodeRegisteredMessage.pattern)

        val registeredVNodes = output.lines().filter { it.matches(vNodeRegisteredMessage) }.associate {
            val commonName = it.split("CN=").last().split(",").first()
            val shortHash = it.split(" with shortHash ").last().split(" registered.").first()
            commonName to shortHash
        }
        assertThat(registeredVNodes.keys).containsExactlyInAnyOrderElementsOf(expectedCommonNames)
        return registeredVNodes
    }

    private fun runListCPIsTaskAndVerifyOutput(expectedCpiNames: List<String>): Map<String, String> {
        val listCpisResult = executeWithRunner(
            LIST_CPIS_TASK_NAME,
            "--info",
            "--stacktrace",
            forwardOutput = true,
            isStaticNetwork = true
        )
        val actualCpis = listCpisResult.output.lines().filter { it.contains("1.0-SNAPSHOT") }
            .associate {
                val fields = it.split(Regex("\\s+"))
                fields[0] to fields[2]
            }
        assertThat(actualCpis.keys).hasSize(expectedCpiNames.size)
        assertThat(actualCpis.keys).containsExactlyInAnyOrderElementsOf(expectedCpiNames)
        return actualCpis
    }

    private fun runListVNodesTaskAndVerifyOutput(
        expectedCpiNames: List<String>,
        expectedCommonNames: List<String>,
        expectedShortHashes: List<String>
    ) {
        val listVNodesResult = executeWithRunner(
            LIST_VNODES_TASK_NAME,
            "--info",
            "--stacktrace",
            forwardOutput = true,
            isStaticNetwork = true
        )

        val vNodes = listVNodesResult.output.lines().filter { it.contains("CN=") }.map {
            val (cpiName, shortHash) = it.split(Regex("\\s+")).take(2)
            val commonName = it.split("CN=").last().split(",").first()
            Triple(cpiName, shortHash, commonName)
        }
        assertThat(vNodes).hasSameSizeAs(expectedCommonNames)

        assertThat(vNodes.map { it.first }.toSet()).containsExactlyInAnyOrderElementsOf(expectedCpiNames)
        assertThat(vNodes.map { it.second }).containsExactlyInAnyOrderElementsOf(expectedShortHashes)
        assertThat(vNodes.map { it.third }).containsExactlyInAnyOrderElementsOf(expectedCommonNames)
    }

    private fun verifyNotaryCpks() {
        val notaryCpks = restClient.cpiClient.getCpi().cpis.filter { it.id.cpiName == "NotaryServer" }.firstOrNull()?.cpks
        assertThat(notaryCpks).isNotNull
        assertThat(notaryCpks).isNotEmpty
        assertThat(notaryCpks).allSatisfy { assertThat(it.id.name).contains("com.r3.corda.notary") }
    }
}
