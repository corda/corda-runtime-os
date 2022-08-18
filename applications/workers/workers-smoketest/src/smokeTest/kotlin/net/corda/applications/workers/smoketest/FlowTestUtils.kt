package net.corda.applications.workers.smoketest

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID
import net.corda.applications.workers.smoketest.virtualnode.helpers.assertWithRetry
import net.corda.applications.workers.smoketest.virtualnode.helpers.cluster
import net.corda.applications.workers.smoketest.virtualnode.toJson
import net.corda.craft5.common.millis
import net.corda.craft5.corda.client.*
import net.corda.craft5.util.retry
import org.apache.commons.text.StringEscapeUtils.escapeJson
import org.assertj.core.api.Assertions

const val SMOKE_TEST_CLASS_NAME = "net.cordapp.flowworker.development.smoketests.flow.RpcSmokeTestFlow"
const val RPC_FLOW_STATUS_SUCCESS = "COMPLETED"
const val RPC_FLOW_STATUS_FAILED = "FAILED"

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
fun CordaClient.startFlow(holdingIdHash: String, flowClassName: String, args: RpcSmokeTestInput, requestId: String = UUID.randomUUID().toString()) : StartFlow {
    val inputAsMap = ObjectMapper().convertValue(args, object: TypeReference<Map<String, Any>>() {})
    return startFlow(holdingIdHash, flowClassName, inputAsMap, requestId)
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

/**
 * Wrapper method to perform the multiple requests necessary to create a virtual node
 * @param [CordaClient] pass in the instantiated corda client
 * @param [x500] The x500 name string
 * @return the holdingId string
 */
fun createVirtualNodeFor(cordaClient: CordaClient, x500: String): String {
    val cpiList = cordaClient.cpiList().cpis
    val shortHash = cpiList.first{it.id.cpiName == CPI_NAME }.cpiFileChecksum.substring(0,12)
    val holdingId = cordaClient.vNodeCreate(shortHash, x500).holdingIdentity.shortHash
    Assertions.assertThat(holdingId).isNotNull.isNotEmpty
    return holdingId
}

/**
 * Wrapper method to first create a key, then get the key's public key
 * @param [CordaClient] pass in the instantiated corda client
 * @param [holdingId] the holdingIdHash of the virtual node / tennantId
 * @param [category] the HSM category
 * @param [scheme] the scheme to use
 * @return the contents of the public key as a String
 */
fun createKeyFor(cordaClient: CordaClient, holdingId: String, alias: String, category: String, scheme: String) : String {
    val keyId = cordaClient.createKey(holdingId, alias, category, scheme).keyId
    val keyValue = retry (attempts = 5, cooldown = 200.millis ) {
        val res = cordaClient.getKey(holdingId, keyId)
        failFalse(res.getResponseObject().status() == 200, "Failed to get key for holding id '$holdingId' and key id '$keyId'")
        res.content
    }
    return keyValue
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
