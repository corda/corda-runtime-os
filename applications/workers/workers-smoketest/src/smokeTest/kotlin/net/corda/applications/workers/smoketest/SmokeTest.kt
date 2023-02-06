package net.corda.applications.workers.smoketest

import com.fasterxml.jackson.databind.JsonNode
import net.corda.applications.workers.smoketest.virtualnode.helpers.assertWithRetry
import net.corda.applications.workers.smoketest.virtualnode.helpers.cluster
import net.corda.applications.workers.smoketest.websocket.client.useWebsocketConnection
import net.corda.data.flow.output.FlowStates
import net.corda.httprpc.ResponseCode.CONFLICT
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Duration
import java.util.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SmokeTest {
    companion object {
        private val testRunUniqueId = UUID.randomUUID().toString().substring(0, 6)
        private val aliceX500 = "CN=Alice-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private val bobX500 = "CN=Bob-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"
        private val notaryX500 = "CN=Notary-${testRunUniqueId}, OU=Application, O=R3, L=London, C=GB"
        private val staticMemberList = listOf(
            aliceX500,
            bobX500,
            notaryX500
        )

        private val requestId = "$testRunUniqueId"

        private val cpiName = "${TEST_CPI_NAME}_$testRunUniqueId"

        private var cpiNameHash = ""
        private var aliceHoldingId = ""
        private var bobHoldingId = ""
        private var notaryHoldingId = ""
    }

    @Test
    @Order(5)
    fun `import codesigner certificate`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
            assertWithRetry {
                // Certificate upload can be slow in the combined worker, especially after it has just started up.
                timeout(Duration.ofSeconds(100))
                interval(Duration.ofSeconds(1))
                command { importCertificate(CODE_SIGNER_CERT, "code-signer", "cordadev") }
                condition { it.code == 204 }
            }
        }
    }

    @Test
    @Order(10)
    fun `Upload the test CPI`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            val requestId = cpiUpload(TEST_CPB_LOCATION, GROUP_ID, staticMemberList, cpiName)
                .let { it.toJson()["id"].textValue() }
            assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

            // BUG:  returning "OK" feels 'weakly' typed
            val json = assertWithRetry {
                // CPI upload can be slow in the combined worker, especially after it has just started up.
                timeout(Duration.ofSeconds(100))
                interval(Duration.ofSeconds(2))
                command { cpiStatus(requestId) }
                condition {
                    it.code == 200 && it.toJson()["status"].textValue() == "OK"
                }
                immediateFailCondition {
                    it.code == CONFLICT.statusCode
                            && null != it.toJson()["details"]
                            && it.toJson()["details"]["code"].textValue().equals(CONFLICT.toString())
                            && null != it.toJson()["title"]
                            && it.toJson()["title"].textValue().contains("already uploaded")
                }
            }.toJson()

            cpiNameHash = json["cpiFileChecksum"].textValue()
            assertThat(cpiNameHash).isNotNull.isNotEmpty
        }
    }

    @Test
    @Order(15)
    fun `create the virtual nodes for bob and alice and a notary`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            var vNodeJson = assertWithRetry {
                command { vNodeCreate(cpiNameHash, aliceX500) }
                condition { it.code == 200 }
                failMessage("Failed to create the virtual node for '${aliceX500}'")
            }.toJson()

            aliceHoldingId = vNodeJson.getTextFromPath("holdingIdentity", "shortHash")
            assertThat(aliceHoldingId)
                .isNotNull.isNotEmpty.withFailMessage { "Failed to find a holding ID for Bob" }

            vNodeJson = assertWithRetry {
                command { vNodeCreate(cpiNameHash, bobX500) }
                condition { it.code == 200 }
                failMessage("Failed to create the virtual node for '${bobX500}'")
            }.toJson()

            bobHoldingId = vNodeJson.getTextFromPath("holdingIdentity", "shortHash")
            assertThat(bobHoldingId)
                .isNotNull.isNotEmpty.withFailMessage { "Failed to find a holding ID for Bob" }

            vNodeJson = assertWithRetry {
                command { vNodeCreate(cpiNameHash, notaryX500) }
                condition { it.code == 200 }
                failMessage("Failed to create the virtual node for '${notaryX500}'")
            }.toJson()

            notaryHoldingId = vNodeJson.getTextFromPath("holdingIdentity", "shortHash")
            assertThat(notaryHoldingId)
                .isNotNull.isNotEmpty.withFailMessage { "Failed to find a holding ID for Notary" }
        }
    }

    @Order(20)
    @Test
    fun `run the test all flow`() {
        val flowStatusApi = "/flow/$aliceHoldingId/$requestId"

        useWebsocketConnection(flowStatusApi) { wsHandler ->
            val requestBody = RpcSmokeTestInput().apply {
                command = "echo"
                data = mapOf("echo_value" to "hello")
            }

            val requestId = startRpcFlow(aliceHoldingId, requestBody)

            val result = awaitRpcFlowFinished(aliceHoldingId, requestId)

            assertThat(result.flowStatus).isEqualTo(RPC_FLOW_STATUS_SUCCESS).withFailMessage{
                "The flow failed with the following error '${result.flowError?.type} - ${result.flowError?.message}'"
            }

            // Now check the web socket status check received the messages we expect
            eventually(Duration.ofSeconds(300)) {
                assertThat(wsHandler.messageQueueSnapshot).hasSize(3)
            }
            val messageQueue = wsHandler.messageQueueSnapshot
            assertThat(messageQueue[0]).contains(FlowStates.START_REQUESTED.name)
            assertThat(messageQueue[1]).contains(FlowStates.RUNNING.name)
            assertThat(messageQueue[2]).contains(FlowStates.COMPLETED.name)
        }
    }

    private fun JsonNode.getTextFromPath(vararg paths: String): String {
        val root = this
        var currentNode = root
        for (fieldName in paths) {
            assertThat(currentNode.has(fieldName))
                .isTrue()
                .withFailMessage("Failed find $'fieldName' in path '${paths.joinToString()}' in '$root'")
            currentNode = currentNode[fieldName]
        }
        return currentNode.textValue()
    }
}