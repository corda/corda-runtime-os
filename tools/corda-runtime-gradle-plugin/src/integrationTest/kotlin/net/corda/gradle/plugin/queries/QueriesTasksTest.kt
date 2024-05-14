package net.corda.gradle.plugin.queries

import net.corda.gradle.plugin.FunctionalBaseTest
import net.corda.libs.cpiupload.endpoints.v1.CpiIdentifier
import net.corda.libs.cpiupload.endpoints.v1.CpiMetadata
import net.corda.libs.cpiupload.endpoints.v1.GetCPIsResponse
import net.corda.libs.virtualnode.endpoints.v1.types.HoldingIdentity
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodeInfo
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodes
import net.corda.virtualnode.OperationalStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.Instant

class QueriesTasksTest : FunctionalBaseTest() {
    @Test
    fun shouldContainSupportingTasks() {
        assertNotNull(executeWithRunner("tasks", "--group", CLUSTER_QUERY_GROUP).tasks)
    }

    @Test
    fun listVNodesFailsConnectionRefused() {
        appendCordaRuntimeGradlePluginExtension()
        val result = executeAndFailWithRunner(LIST_VNODES_TASK_NAME)
        assertThat(result.output)
            .contains("connect to $restHostname")
            .contains("Connection refused")
    }

    @Test
    fun listCPIsFailsConnectionRefused() {
        appendCordaRuntimeGradlePluginExtension()
        val result = executeAndFailWithRunner(LIST_CPIS_TASK_NAME)
        assertThat(result.output)
            .contains("connect to $restHostname")
            .contains("Connection refused")
    }

    @Test
    fun listVNodesMockedResponse() {
        startMockedApp()
        appendCordaRuntimeGradlePluginExtension(restProtocol = "http")
        mockGetVirtualNodeProtocolVersion()

        val testVNodeIds = listOf("One", "Two")
        mockListVNodesResponse(testVNodeIds)

        val taskOutput = executeWithRunner(LIST_VNODES_TASK_NAME).output
        val testVNodesInfoOutputAssertions = testVNodeIds.map {
            {
                val pattern = Regex("cpiName$it\\s+shortHash$it\\s+x500Name$it\\s+")
                assertNotNull(taskOutput.lines().firstOrNull { it.matches(pattern) })
            }
        }
        assertAll(
            heading = "Task output should include info on test VNodes; actual output:\n${taskOutput}",
            testVNodesInfoOutputAssertions
        )
    }

    @Test
    fun listCPIsMockedResponse() {
        startMockedApp()
        appendCordaRuntimeGradlePluginExtension(restProtocol = "http")
        mockGetCPIProtocolVersion()

        val testInputs = listOf("testOne", "test2")
        mockGetCPIResponse(testInputs)
        val taskOutput = executeWithRunner(LIST_CPIS_TASK_NAME).output
        val testCPIsInfoOutputAssertions = testInputs.map {
            {
                assertTrue(taskOutput.contains("cpiName$it"))
            }
        }
        assertAll(
            heading = "Task output should include info on test CPIs; actual output:\n${taskOutput}",
            testCPIsInfoOutputAssertions
        )
    }

    private fun mockListVNodesResponse(testVNodeIds: List<String>) {
        val vNodeResponsePayload = testVNodeIds.map {
            val holdingId = HoldingIdentity(
                x500Name = "x500Name$it",
                groupId = "groupId",
                shortHash = "shortHash$it",
                fullHash = "fullHash"
            )
            val cpiId = CpiIdentifier(cpiName = "cpiName$it", cpiVersion = "cpiVersion", signerSummaryHash = null)
            VirtualNodeInfo(
                holdingIdentity = holdingId,
                cpiIdentifier = cpiId,
                cryptoDmlConnectionId = "",
                flowOperationalStatus = OperationalStatus.ACTIVE,
                flowP2pOperationalStatus = OperationalStatus.ACTIVE,
                flowStartOperationalStatus = OperationalStatus.ACTIVE,
                uniquenessDmlConnectionId = "",
                vaultDbOperationalStatus = OperationalStatus.ACTIVE,
                vaultDmlConnectionId = "",
                externalMessagingRouteConfiguration = null
            )
        }.let { VirtualNodes(it) }

        app.get("/api/v5_2/virtualnode") { ctx -> ctx.json(vNodeResponsePayload) }
    }

    private fun mockGetCPIResponse(inputs: List<String>) {
        val responseToUse = inputs.map {
            CpiMetadata(
                id = CpiIdentifier(
                    cpiName = "cpiName$it",
                    cpiVersion = "1.0",
                    signerSummaryHash = ""
                ),
                cpiFileChecksum = "1234567890AB",
                cpiFileFullChecksum = "1234567890ABCDEF1234567890ABCDEF1234567890",
                cpks = emptyList(),
                groupPolicy = "",
                timestamp = Instant.now()
            )
        }.let { GetCPIsResponse(it) }
        app.get("/api/v5_2/cpi") { ctx -> ctx.json(responseToUse) }
    }

    private fun mockGetCPIProtocolVersion() {
        app.get("/api/v5_2/cpi/getprotocolversion") { ctx -> ctx.result("1") }
    }

    private fun mockGetVirtualNodeProtocolVersion() {
        app.get("/api/v5_2/virtualnode/getprotocolversion") { ctx -> ctx.result("1") }
    }
}
