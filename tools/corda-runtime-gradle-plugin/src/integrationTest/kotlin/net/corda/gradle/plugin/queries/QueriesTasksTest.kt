package net.corda.gradle.plugin.queries

import net.corda.gradle.plugin.FunctionalBaseTest
import net.corda.gradle.plugin.dtos.CpiIdentifierDTO
import net.corda.gradle.plugin.dtos.CpiMetadataDTO
import net.corda.gradle.plugin.dtos.GetCPIsResponseDTO
import net.corda.gradle.plugin.dtos.HoldingIdentityDTO
import net.corda.gradle.plugin.dtos.VirtualNodeInfoDTO
import net.corda.gradle.plugin.dtos.VirtualNodesDTO
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class QueriesTasksTest : FunctionalBaseTest() {
    @Test
    fun shouldContainSupportingTasks() {
        assertNotNull(executeWithRunner("tasks", "--group", CLUSTER_QUERY_GROUP).tasks)
    }

    @Test
    fun listVNodesFailsConnectionRefused() {
        appendCordaRuntimeGradlePluginExtension()
        val result = executeAndFailWithRunner(LIST_VNODES_TASK_NAME)
        assertTrue(result.output.contains("Connect to $restHostnameWithPort"))
        assertTrue(result.output.contains("Connection refused"))
    }

    @Test
    fun listCPIsFailsConnectionRefused() {
        appendCordaRuntimeGradlePluginExtension()
        val result = executeAndFailWithRunner(LIST_CPIS_TASK_NAME)
        assertTrue(result.output.contains("Connect to $restHostnameWithPort"))
        assertTrue(result.output.contains("Connection refused"))
    }

    @Test
    fun listVNodesMockedResponse() {
        startMockedApp()
        appendCordaRuntimeGradlePluginExtension(restProtocol = "http")

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

        val testCPIsIds = listOf("One", "Two")
        mockGetCPIResponse(testCPIsIds)

        val taskOutput = executeWithRunner(LIST_CPIS_TASK_NAME).output
        val testCPIsInfoOutputAssertions = testCPIsIds.map {
            {
                val pattern = Regex("cpiName$it\\s+cpiVersion$it\\s+checksum$it\\s+")
                assertNotNull(taskOutput.lines().firstOrNull { it.matches(pattern) })
            }
        }
        assertAll(
            heading = "Task output should include info on test CPIs; actual output:\n${taskOutput}",
            testCPIsInfoOutputAssertions
        )
    }

    private fun mockListVNodesResponse(testVNodeIds: List<String>) {
        val vNodeResponsePayload = testVNodeIds.map {
            val holdingId = HoldingIdentityDTO("fullHash", "groupId", "shortHash$it", "x500Name$it")
            val cpiId = CpiIdentifierDTO("cpiName$it", "cpiVersion")
            VirtualNodeInfoDTO(holdingId, cpiId)
        }.let { VirtualNodesDTO(it) }

        app.get("/api/v1/virtualnode") { ctx -> ctx.json(vNodeResponsePayload) }
    }

    private fun mockGetCPIResponse(testCPIsIds: List<String>) {
        val getCpiResponsePayload = testCPIsIds.map {
            val cpiId = CpiIdentifierDTO("cpiName$it", "cpiVersion$it")
            CpiMetadataDTO("checksum$it", cpiId)
        }.let { GetCPIsResponseDTO(it) }

        app.get("/api/v1/cpi") { ctx -> ctx.json(getCpiResponsePayload) }
    }
}
