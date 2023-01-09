package net.corda.applications.workers.smoketest

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.contains
import net.corda.applications.workers.smoketest.virtualnode.helpers.assertWithRetry
import net.corda.applications.workers.smoketest.virtualnode.helpers.cluster
import net.corda.httprpc.ResponseCode.OK
import net.corda.test.util.eventually
import net.corda.v5.base.util.seconds
import org.apache.commons.text.StringEscapeUtils.escapeJson
import org.assertj.core.api.Assertions.assertThat
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID

const val SMOKE_TEST_CLASS_NAME = "net.cordapp.testing.smoketests.flow.RpcSmokeTestFlow"
const val RPC_FLOW_STATUS_SUCCESS = "COMPLETED"
const val RPC_FLOW_STATUS_FAILED = "FAILED"

fun FlowStatus.getRpcFlowResult(): RpcSmokeTestOutput =
    ObjectMapper().readValue(this.flowResult!!, RpcSmokeTestOutput::class.java)

fun startRpcFlow(
    holdingId: String,
    args: RpcSmokeTestInput,
    expectedCode: Int = 202,
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

fun startRpcFlow(holdingId: String, args: Map<String, Any>, flowName: String, expectedCode: Int = 202): String {
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
            condition { it.code == expectedCode }
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

fun getOrCreateVirtualNodeFor(x500: String, cpiName: String): String {
    return cluster {
        endpoint(CLUSTER_URI, USERNAME, PASSWORD)
        // be a bit patient for the CPI info to get there in case it has just been uploaded
        val json = eventually(
            duration = Duration.ofSeconds(30)
        ) {
            val response = cpiList().toJson()
            assertThat(response.contains("cpis"))
            val cpis = response["cpis"]
            val cpi = cpis.toList().firstOrNull { it["id"]["cpiName"].textValue() == cpiName }
            assertThat(cpi).isNotNull
            cpi!!
        }
        val hash = truncateLongHash(json["cpiFileChecksum"].textValue())

        val vNodesJson = assertWithRetry {
            command { vNodeList() }
            condition { it.code == 200 }
            failMessage("Failed to retrieve virtual nodes")
        }.toJson()

        val vNodeJson = if (vNodesJson.findValuesAsText("x500Name").contains(x500)) {
            vNodeList().toJson()["virtualNodes"].toList().first {
                it["holdingIdentity"]["x500Name"].textValue() == x500
            }
        } else {
            val vNodeInfo = assertWithRetry {
                command { vNodeCreate(hash, x500) }
                condition { it.code == 200 }
                failMessage("Failed to create the virtual node for '$x500'")
            }.toJson()
            val holdingId = vNodeInfo["holdingIdentity"]["shortHash"].textValue()

            // Wait for the vNode creation to propagate through the system before moving on
            eventually {
                assertThat(
                    vNodeList().toJson()["virtualNodes"].toList().firstOrNull {
                        it["holdingIdentity"]["shortHash"].textValue() == holdingId
                    }
                ).isNotNull
            }

            vNodeInfo
        }

        val holdingId = vNodeJson["holdingIdentity"]["shortHash"].textValue()
        assertThat(holdingId).isNotNull.isNotEmpty
        holdingId
    }
}

fun registerMember(holdingIdentityShortHash: String, isNotary: Boolean = false) {
    return cluster {
        endpoint(CLUSTER_URI, USERNAME, PASSWORD)

        val membershipJson = assertWithRetry {
            command { registerMember(holdingIdentityShortHash, isNotary) }
            condition { it.code == 200 }
            failMessage("Failed to register the member to the network '$holdingIdentityShortHash'")
        }.toJson()

        val registrationStatus = membershipJson["registrationStatus"].textValue()
        assertThat(registrationStatus).isEqualTo("SUBMITTED")

        assertWithRetry {
            // Use a fairly long timeout here to give plenty of time for the other side to respond. Longer
            // term this should be changed to not use the RPC message pattern and have the information available in a
            // cache on the RPC worker, but for now this will have to suffice.
            timeout(60.seconds)
            interval(1.seconds)
            command { getRegistrationStatus(holdingIdentityShortHash) }
            condition {
                it.toJson().firstOrNull()?.get("registrationStatus")?.textValue() == "APPROVED"
            }
            failMessage("Registration was not completed for $holdingIdentityShortHash")
        }
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
        }.toJson()
        assertWithRetry {
            command { getKey(holdingId, keyId["id"].textValue()) }
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

/**
 * Transform a Corda Package Bundle (CPB) into a Corda Package Installer (CPI) by adding the default group policy
 * used by smoke tests and upload the resulting CPI to the system if it doesn't already exist.
 */
fun conditionallyUploadCordaPackage(name: String, cpb: String, groupId: String, staticMemberNames: List<String>) {
    return cluster {
        endpoint(CLUSTER_URI, USERNAME, PASSWORD)

        val cpis = cpiList().toJson()["cpis"]
        val existingCpi = cpis.toList().firstOrNull { it["id"]["cpiName"].textValue() == name }

        if (existingCpi == null) {
            val uploadResponse = cpiUpload(cpb, groupId, staticMemberNames, name)
            assertThat(uploadResponse.code).isEqualTo(OK.statusCode)
            assertThat(uploadResponse.toJson()["id"].textValue()).isNotEmpty
            val responseStatusId = uploadResponse.toJson()["id"].textValue()

            assertWithRetry {
                timeout(Duration.ofSeconds(100))
                interval(Duration.ofSeconds(2))
                command { cpiStatus(responseStatusId) }
                condition { it.code == OK.statusCode && it.toJson()["status"].textValue() == OK.toString() }
            }
        }
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
