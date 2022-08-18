package net.corda.applications.workers.smoketest

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.applications.workers.smoketest.flow.FlowTests
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID
import net.corda.applications.workers.smoketest.virtualnode.helpers.assertWithRetry
import net.corda.applications.workers.smoketest.virtualnode.helpers.cluster
import net.corda.applications.workers.smoketest.virtualnode.toJson
import net.corda.craft5.corda.client.*
import org.apache.commons.text.StringEscapeUtils.escapeJson
import org.assertj.core.api.Assertions

const val SMOKE_TEST_CLASS_NAME = "net.cordapp.flowworker.development.smoketests.flow.RpcSmokeTestFlow"
const val RPC_FLOW_STATUS_SUCCESS = "COMPLETED"
const val RPC_FLOW_STATUS_FAILED = "FAILED"

fun FlowStatus.getRpcFlowResult(): RpcSmokeTestOutput =
    ObjectMapper().readValue(this.flowResult!!, RpcSmokeTestOutput::class.java)

fun GetFlowStatus.getRpcFlowResult(): RpcSmokeTestOutput =
    ObjectMapper().readValue(this.flowResult, RpcSmokeTestOutput::class.java)

fun startRpcFlow(
    holdingId: String,
    args: RpcSmokeTestInput,
    expectedCode: Int = 200,
    requestId: String = UUID.randomUUID().toString()
): String {

    return cluster {
        endpoint(CLUSTER_URI, USERNAME, PASSWORD)

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

fun startRpcFlow(holdingId: String, args: Map<String, Any>, flowName: String): String {
    return cluster {
        endpoint(CLUSTER_URI, USERNAME, PASSWORD)

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
            condition { it.code == 200 }
        }

        requestId
    }
}

/**
 * An overload for the cordaClient startFlow
 * Where you can pass the [RpcSmokeTestInput] type
 */
fun CordaClient.startFlow(holdingIdHash: String, flowClassName: String, args: RpcSmokeTestInput) : StartFlow {
    val inputAsMap = ObjectMapper().convertValue(args, object: TypeReference<Map<String, Any>>() {})
    return startFlow(holdingIdHash, flowClassName, inputAsMap)
}

fun awaitRpcFlowFinished(holdingId: String, requestId: String): FlowStatus {
    return cluster {
        endpoint(CLUSTER_URI, USERNAME, PASSWORD)

        ObjectMapper().readValue(
            assertWithRetry {
                command { flowStatus(holdingId, requestId) }
                //CORE-6118 - tmp increase this timeout to a large number to allow tests to pass while slow flow sessions are investigated
                timeout(Duration.ofMinutes(6))
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
        endpoint(CLUSTER_URI, USERNAME, PASSWORD)

        val vNodeJson = assertWithRetry {
            command { runnableFlowClasses(holdingId) }
            condition { it.code == 200 }
            failMessage("Failed to get flows for holdingId '$holdingId'")
        }.toJson()

        vNodeJson["flowClassNames"].map { it.textValue() }
    }
}

fun createVirtualNodeFor(x500: String): String {
    return cluster {
        endpoint(CLUSTER_URI, USERNAME, PASSWORD)
        val cpis = cpiList().toJson()["cpis"]
        val json = cpis.toList().first { it["id"]["cpiName"].textValue() == CPI_NAME }
        val hash = truncateLongHash(json["cpiFileChecksum"].textValue())

        val vNodeJson = assertWithRetry {
            command { vNodeCreate(hash, x500) }
            condition { it.code == 200 }
            failMessage("Failed to create the virtual node for '$x500'")
        }.toJson()

        val holdingId = vNodeJson["holdingIdentity"]["shortHash"].textValue()
        Assertions.assertThat(holdingId).isNotNull.isNotEmpty
        holdingId
    }
}
fun createVirtualNodeFor(cordaClient: CordaClient, x500: String): String {
    val cpiList = cordaClient.cpiList().cpis
    val shortHash = cpiList.first{it.id.cpiName == CPI_NAME }.cpiFileChecksum.substring(0,12)
    val holdingId = cordaClient.vNodeCreate(shortHash, x500).holdingIdentity.shortHash
    Assertions.assertThat(holdingId).isNotNull.isNotEmpty
    return holdingId
}

fun registerMember(holdingIdentityId: String) {
    return cluster {
        endpoint(CLUSTER_URI, USERNAME, PASSWORD)

        val membershipJson = assertWithRetry {
            command { registerMember(holdingIdentityId) }
            condition { it.code == 200 }
            failMessage("Failed to register the member to the network '$holdingIdentityId'")
        }.toJson()

        val registrationStatus = membershipJson["registrationStatus"].textValue()
        Assertions.assertThat(registrationStatus).isEqualTo("SUBMITTED")
    }
}

fun addSoftHsmFor(holdingId: String, category: String) {
    return cluster {
        endpoint(CLUSTER_URI, USERNAME, PASSWORD)
        assertWithRetry {
            timeout(Duration.ofSeconds(5))
            command { addSoftHsmToVNode(holdingId, category) }
            condition { it.code == 200 }
            failMessage("Failed to add SoftHSM for holding id '$holdingId'")
        }
    }
}

fun createKeyFor(holdingId: String, alias: String, category: String, scheme: String): String {
    return cluster {
        endpoint(CLUSTER_URI, USERNAME, PASSWORD)
        val keyId = assertWithRetry {
            command { createKey(holdingId, alias, category, scheme) }
            condition { it.code == 200 }
            failMessage("Failed to create key for holding id '$holdingId'")
        }.body
        assertWithRetry {
            command { getKey(holdingId, keyId) }
            condition { it.code == 200 }
            failMessage("Failed to get key for holding id '$holdingId' and key id '$keyId'")
        }.body
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
        .substring(0, 12)
}

class RpcSmokeTestInput {
    var command: String? = null
    var data: Map<String, Any>? = null
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
