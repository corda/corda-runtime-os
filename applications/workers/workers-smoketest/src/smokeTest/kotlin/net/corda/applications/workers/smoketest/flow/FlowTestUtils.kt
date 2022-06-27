package net.corda.applications.workers.smoketest.flow

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID
import net.corda.applications.workers.smoketest.CLUSTER_URI
import net.corda.applications.workers.smoketest.CPI_NAME
import net.corda.applications.workers.smoketest.PASSWORD
import net.corda.applications.workers.smoketest.USERNAME
import net.corda.applications.workers.smoketest.X500_BOB
import net.corda.applications.workers.smoketest.truncateLongHash
import net.corda.applications.workers.smoketest.virtualnode.helpers.assertWithRetry
import net.corda.applications.workers.smoketest.virtualnode.helpers.cluster
import net.corda.applications.workers.smoketest.virtualnode.toJson
import org.apache.commons.text.StringEscapeUtils.escapeJson
import org.assertj.core.api.Assertions

const val SMOKE_TEST_CLASS_NAME = "net.cordapp.flowworker.development.flows.RpcSmokeTestFlow"
const val X500_SESSION_USER1 = "CN=SU1, OU=Application, O=R3, L=London, C=GB"
const val X500_SESSION_USER2 = "CN=SU2, OU=Application, O=R3, L=London, C=GB"
const val RPC_FLOW_STATUS_SUCCESS = "COMPLETED"
const val RPC_FLOW_STATUS_FAILED = "FAILED"

fun FlowStatus.getRpcFlowResult(): RpcSmokeTestOutput =
    ObjectMapper().readValue(this.flowResult!!, RpcSmokeTestOutput::class.java)

fun startRpcFlow(holdingId: String, args: RpcSmokeTestInput): String {

    return cluster {
        endpoint(CLUSTER_URI, USERNAME, PASSWORD)

        val requestId = UUID.randomUUID().toString()

        assertWithRetry {
            command {
                flowStart(
                    holdingId,
                    requestId,
                    SMOKE_TEST_CLASS_NAME,
                    """{ "requestBody":  "${escapeJson(ObjectMapper().writeValueAsString(args))}" }"""
                )
            }
            condition { it.code == 200 }
        }

        requestId
    }
}

fun awaitRpcFlowFinished(holdingId: String, requestId: String): FlowStatus {
    return cluster {
        endpoint(CLUSTER_URI, USERNAME, PASSWORD)

        ObjectMapper().readValue(
            assertWithRetry {
                command { flowStatus(holdingId, requestId) }
                timeout(Duration.ofSeconds(20))
                condition {
                    it.code == 200 &&
                            (it.toJson()["flowStatus"].textValue() == RPC_FLOW_STATUS_SUCCESS ||
                                    it.toJson()["flowStatus"].textValue() == RPC_FLOW_STATUS_FAILED)
                }
            }.body, FlowStatus::class.java
        )
    }
}

fun awaitMultipleRpcFlowFinished(holdingId: String, expectedFlowCount: Int) {
    return cluster {
        endpoint(CLUSTER_URI, USERNAME, PASSWORD)

        assertWithRetry {
            command { multipleFlowStatus(holdingId) }
            timeout(Duration.ofSeconds(20))
            condition {
                val json = it.toJson()
                val flowStatuses = json["httpFlowStatusResponses"]
                val allStatusComplete = flowStatuses.map { flowStatus ->
                    flowStatus["flowStatus"].textValue() == RPC_FLOW_STATUS_SUCCESS ||
                            flowStatus["flowStatus"].textValue() == RPC_FLOW_STATUS_FAILED
                }.all { true }
                it.code == 200 && flowStatuses.size() == expectedFlowCount && allStatusComplete
            }
        }
    }
}

fun createVirtualNodeFor(x500: String): String {
    return cluster {
        endpoint(CLUSTER_URI, USERNAME, PASSWORD)
        val cpis = cpiList().toJson()["cpis"]
        val json = cpis.toList().first { it["id"]["cpiName"].textValue() == CPI_NAME }
        val hash = truncateLongHash(json["fileChecksum"].textValue())

        val vNodeJson = assertWithRetry {
            command { vNodeCreate(hash, x500) }
            condition { it.code == 200 }
            failMessage("Failed to create the virtual node for '$X500_BOB'")
        }.toJson()

        val holdingId = vNodeJson["holdingIdHash"].textValue()
        Assertions.assertThat(holdingId).isNotNull.isNotEmpty
        holdingId
    }
}

/**
 *This is a crude method for getting the holdingID short hash,it assumes the formatting of the
 *x500 is a perfect match for the internal formatting used in the platform code.
 */
fun getHoldingIdShortHash(x500Name: String, groupId: String): String {
    val s = (x500Name + groupId)
    val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
    return digest.digest(s.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte).uppercase() }
        .substring(0,12)
}

class RpcSmokeTestInput {
    var command: String? = null
    var data: Map<String, String>? = null
}

@JsonIgnoreProperties(ignoreUnknown = true)
class RpcSmokeTestOutput {
    var command: String? = null
    var result: String? = null
}

@JsonIgnoreProperties(ignoreUnknown = true)
class FlowStatus {
    var flowStatus: String? = null
    val flowResult: String? = null
    val flowError: FlowError? = null
}

@JsonIgnoreProperties(ignoreUnknown = true)
class FlowError {
    var type: String? = null
    var message: String? = null
}