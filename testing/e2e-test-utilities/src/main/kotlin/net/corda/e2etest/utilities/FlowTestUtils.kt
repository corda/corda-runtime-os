package net.corda.e2etest.utilities

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.NullNode
import org.apache.commons.text.StringEscapeUtils.escapeJson
import java.time.Duration
import java.util.UUID

const val SMOKE_TEST_CLASS_NAME = "com.r3.corda.testing.smoketests.flow.RpcSmokeTestFlow"
const val RPC_FLOW_STATUS_SUCCESS = "COMPLETED"
const val RPC_FLOW_STATUS_FAILED = "FAILED"

fun FlowStatus.getRpcFlowResult(): RpcSmokeTestOutput =
    this.flowResult!!.traverse(ObjectMapper()).readValueAs(RpcSmokeTestOutput::class.java)

fun startRpcFlow(
    holdingId: String,
    args: RpcSmokeTestInput,
    expectedCode: Int = 202,
    requestId: String = UUID.randomUUID().toString()
): String {

    return cluster {
        endpoint(
            CLUSTER_URI,
            USERNAME,
            PASSWORD
        )

        assertWithRetry {
            command {
                flowStart(
                    holdingId,
                    requestId,
                    SMOKE_TEST_CLASS_NAME,
                    escapeJson(ObjectMapper().writeValueAsString(args))
                )
            }
            condition { it.code == expectedCode }
        }

        requestId
    }
}
fun startRpcFlow(
    holdingId: String,
    args: Map<String, Any>,
    flowName: String,
    expectedCode: Int = 202,
    clusterInfo: ClusterInfo = ClusterBInfo,
): String {
    return clusterInfo.cluster {
        val requestId = UUID.randomUUID().toString()

        assertWithRetry {
            command {
                flowStart(
                    holdingId,
                    requestId,
                    flowName,
                    escapeJson(ObjectMapper().writeValueAsString(args))
                )
            }
            condition { it.code == expectedCode }
        }

        requestId
    }
}

fun awaitRpcFlowFinished(
    holdingId: String,
    requestId: String,
    clusterInfo: ClusterInfo = ClusterBInfo,
): FlowStatus {
    return clusterInfo.cluster {
        val jsonNode = ObjectMapper().readTree(
            assertWithRetry {
                command { flowStatus(holdingId, requestId) }
                //CORE-6118 - tmp increase this timeout to a large number to allow tests to pass while slow flow sessions are investigated
                timeout(Duration.ofMinutes(6))
                condition {
                    it.code == 200 &&
                            (it.toJson()["flowStatus"].textValue() == RPC_FLOW_STATUS_SUCCESS ||
                                    it.toJson()["flowStatus"].textValue() == RPC_FLOW_STATUS_FAILED)
                }
            }.body)

        FlowStatus(
            jsonNode[FlowStatus::flowStatus.name]?.textValue(),
            jsonNode[FlowStatus::flowResult.name]?.handlingNulls(),
            jsonNode[FlowStatus::flowError.name]?.handlingNulls()?.asFlowError()
        )
    }
}

private fun JsonNode.handlingNulls(): JsonNode? {
    return when(this) {
        is NullNode -> null
        else -> this
    }
}

fun getFlowStatus(
    holdingId: String,
    requestId: String,
    expectedCode: Int,
    clusterInfo: ClusterInfo = ClusterBInfo,
): FlowStatus {
    return clusterInfo.cluster {
        val jsonNode = ObjectMapper().readTree(
            assertWithRetry {
                command { flowStatus(holdingId, requestId) }
                timeout(Duration.ofMinutes(6))
                condition {
                    it.code == expectedCode
                }
            }.body
        )

        FlowStatus(
            jsonNode[FlowStatus::flowStatus.name]?.textValue(),
            jsonNode[FlowStatus::flowResult.name]?.handlingNulls(),
            jsonNode[FlowStatus::flowError.name]?.handlingNulls()?.asFlowError()
        )
    }
}

private fun JsonNode.asFlowError(): FlowError =
    FlowError(this["type"]?.textValue(), this["message"]?.textValue())

fun awaitMultipleRpcFlowFinished(holdingId: String, expectedFlowCount: Int) {
    return cluster {
        endpoint(
            CLUSTER_URI,
            USERNAME,
            PASSWORD
        )

        assertWithRetry {
            command { multipleFlowStatus(holdingId) }
            timeout(Duration.ofSeconds(20))
            condition {
                val json = it.toJson()
                val flowStatuses = json["flowStatusResponses"]
                val allStatusComplete = flowStatuses.map { flowStatus ->
                    flowStatus["flowStatus"].textValue() == RPC_FLOW_STATUS_SUCCESS ||
                            flowStatus["flowStatus"].textValue() == RPC_FLOW_STATUS_FAILED
                }.all { true }
                it.code == 200 && flowStatuses.size() == expectedFlowCount && allStatusComplete
            }
        }
    }
}

fun getFlowClasses(holdingId: String): List<String> {
    return cluster {
        endpoint(
            CLUSTER_URI,
            USERNAME,
            PASSWORD
        )

        val vNodeJson = assertWithRetry {
            command { runnableFlowClasses(holdingId) }
            condition { it.code == 200 }
            failMessage("Failed to get flows for holdingId '$holdingId'")
        }.toJson()

        vNodeJson["flowClassNames"].map { it.textValue() }
    }
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

data class FlowStatus (
    val flowStatus: String?,
    val flowResult: JsonNode?,
    val flowError: FlowError?
)

data class FlowError (
    val type: String?,
    val message: String?
)
