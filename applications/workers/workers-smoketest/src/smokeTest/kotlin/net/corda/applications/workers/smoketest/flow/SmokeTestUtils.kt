package net.corda.applications.workers.smoketest.flow

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.applications.workers.smoketest.CLUSTER_URI
import net.corda.applications.workers.smoketest.CPI_NAME
import net.corda.applications.workers.smoketest.PASSWORD
import net.corda.applications.workers.smoketest.USERNAME
import net.corda.applications.workers.smoketest.X500_BOB
import net.corda.applications.workers.smoketest.toShortHash
import net.corda.applications.workers.smoketest.virtualnode.helpers.SimpleResponse
import net.corda.applications.workers.smoketest.virtualnode.helpers.assertWithRetry
import net.corda.applications.workers.smoketest.virtualnode.helpers.cluster
import net.corda.applications.workers.smoketest.virtualnode.toJson
import org.apache.commons.text.StringEscapeUtils
import org.assertj.core.api.Assertions
import java.security.MessageDigest
import java.time.Duration
import java.util.*

const val SMOKE_TEST_CLASS_NAME = "net.cordapp.flowworker.development.flows.RpcSmokeTestFlow"
const val X500_SESSION_USER1 = "CN=SU1, OU=Application, O=R3, L=London, C=GB"
const val X500_SESSION_USER2 = "CN=SU2, OU=Application, O=R3, L=London, C=GB"
const val RPC_FLOW_STATUS_COMPLETED = "COMPLETED"
const val RPC_FLOW_STATUS_FAILED = "FAILED"

fun RpcSmokeTestInput.toJsonString(): String = ObjectMapper().writeValueAsString(this)

fun SimpleResponse.toFlowStatus(): FlowStatus = ObjectMapper().readValue(this.body, FlowStatus::class.java)

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
                    """{ "requestBody":  "${StringEscapeUtils.escapeJson(args.toJsonString())}" }"""
                )
            }
            condition { it.code == 200 }
        }

        requestId
    }
}

fun awaitRpcFlowCompletion(holdingId: String, requestId: String): FlowStatus {
    return cluster {
        endpoint(CLUSTER_URI, USERNAME, PASSWORD)

        assertWithRetry {
            command { flowStatus(holdingId, requestId) }
            timeout(Duration.ofSeconds(20))
            condition {
                it.code == 200 &&
                        (it.toJson()["flowStatus"].textValue() == RPC_FLOW_STATUS_COMPLETED ||
                                it.toJson()["flowStatus"].textValue() == RPC_FLOW_STATUS_FAILED)
            }
        }.toFlowStatus()
    }
}

fun createVirtualNodeFor(x500: String): String {
    return cluster {
        endpoint(CLUSTER_URI, USERNAME, PASSWORD)
        val cpis = cpiList().toJson()["cpis"]
        val json = cpis.toList().first { it["id"]["cpiName"].textValue() == CPI_NAME }
        val hash = json["fileChecksum"].textValue().toShortHash()

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
        .toShortHash()
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