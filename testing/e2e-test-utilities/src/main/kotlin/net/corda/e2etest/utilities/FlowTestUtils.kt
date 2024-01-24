@file:Suppress("TooManyFunctions")

package net.corda.e2etest.utilities

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.NullNode
import net.corda.utilities.minutes
import org.apache.commons.text.StringEscapeUtils.escapeJson
import org.junit.jupiter.api.TestInfo
import org.slf4j.LoggerFactory
import java.util.UUID

const val SMOKE_TEST_CLASS_NAME = "com.r3.corda.testing.smoketests.flow.RestSmokeTestFlow"

const val REST_FLOW_STATUS_SUCCESS = "COMPLETED"
const val REST_FLOW_STATUS_FAILED = "FAILED"

private val RETRY_TIMEOUT = 6.minutes

fun startRestFlow(
    holdingId: String,
    args: RestSmokeTestInput,
    expectedCode: Int = 202,
    requestId: String = UUID.randomUUID().toString()
): String {

    return DEFAULT_CLUSTER.cluster {
        assertWithRetryIgnoringExceptions {
            timeout(RETRY_TIMEOUT)
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

fun startRestFlow(
    holdingId: String,
    args: Map<String, Any>,
    flowName: String,
    expectedCode: Int = 202,
    requestId: String = UUID.randomUUID().toString()
) = DEFAULT_CLUSTER.startRestFlow(holdingId, args, flowName, expectedCode, requestId)

fun ClusterInfo.startRestFlow(
    holdingId: String,
    args: Map<String, Any>,
    flowName: String,
    expectedCode: Int = 202,
    requestId: String = UUID.randomUUID().toString()
): String {
    return cluster {

        assertWithRetry {
            timeout(RETRY_TIMEOUT)
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

fun awaitRestFlowFinished(
    holdingId: String,
    requestId: String
) = DEFAULT_CLUSTER.awaitRestFlowFinished(holdingId, requestId)

fun ClusterInfo.awaitRestFlowFinished(holdingId: String, requestId: String): FlowStatus {
    return cluster {
        ObjectMapper().readValue(
            assertWithRetryIgnoringExceptions {
                command { flowStatus(holdingId, requestId) }
                timeout(RETRY_TIMEOUT)
                condition {
                    it.code == 200 &&
                            (it.toJson()["flowStatus"].textValue() == REST_FLOW_STATUS_SUCCESS ||
                                    it.toJson()["flowStatus"].textValue() == REST_FLOW_STATUS_FAILED)
                }
            }.body, FlowStatus::class.java
        )
    }
}

fun awaitRestFlowResult(
    holdingId: String,
    requestId: String
) = DEFAULT_CLUSTER.awaitRestFlowResult(holdingId, requestId)

fun ClusterInfo.awaitRestFlowResult(holdingId: String, requestId: String): FlowResult {
    return cluster {
        val jsonNode = ObjectMapper().readTree(
            assertWithRetryIgnoringExceptions {
                command { flowResult(holdingId, requestId) }
                timeout(RETRY_TIMEOUT)
                condition {
                    it.code == 200 &&
                            (it.toJson()["flowStatus"].textValue() == REST_FLOW_STATUS_SUCCESS ||
                                    it.toJson()["flowStatus"].textValue() == REST_FLOW_STATUS_FAILED)
                }
            }.body
        )

        FlowResult(
            jsonNode[FlowResult::flowStatus.name]?.textValue(),
            jsonNode[FlowResult::json.name]?.handlingNulls(),
            jsonNode[FlowResult::flowError.name]?.handlingNulls()?.asFlowError()
        )
    }
}

private fun JsonNode.handlingNulls(): JsonNode? {
    return when (this) {
        is NullNode -> null
        else -> this
    }
}

@Suppress("unused")
fun getFlowStatus(
    holdingId: String,
    requestId: String,
    expectedCode: Int
) = DEFAULT_CLUSTER.getFlowStatus(holdingId, requestId, expectedCode)

fun ClusterInfo.getFlowStatus(holdingId: String, requestId: String, expectedCode: Int): FlowStatus {
    return cluster {
        ObjectMapper().readValue(
            assertWithRetryIgnoringExceptions {
                command { flowStatus(holdingId, requestId) }
                timeout(RETRY_TIMEOUT)
                condition {
                    it.code == expectedCode
                }
            }.body, FlowStatus::class.java
        )
    }
}

private fun JsonNode.asFlowError(): FlowError {
    val flowError = FlowError()
    flowError.type = this["type"]?.textValue()
    flowError.message = this["message"]?.textValue()
    return flowError
}

@Suppress("unused")
fun awaitMultipleRestFlowFinished(holdingId: String, expectedFlowCount: Int) {
    return DEFAULT_CLUSTER.cluster {
        assertWithRetryIgnoringExceptions {
            command { multipleFlowStatus(holdingId) }
            timeout(RETRY_TIMEOUT)
            condition {
                val json = it.toJson()
                val flowStatuses = json["flowStatusResponses"]
                val allStatusComplete = flowStatuses.map { flowStatus ->
                    flowStatus["flowStatus"].textValue() == REST_FLOW_STATUS_SUCCESS ||
                            flowStatus["flowStatus"].textValue() == REST_FLOW_STATUS_FAILED
                }.all { true }
                it.code == 200 && flowStatuses.size() == expectedFlowCount && allStatusComplete
            }
        }
    }
}

@Suppress("unused")
fun assertStatusFilter(
    holdingId: String,
    expectedCode: Int,
    status: String?
) = DEFAULT_CLUSTER.assertStatusFilter(holdingId, expectedCode, status)

fun ClusterInfo.assertStatusFilter(holdingId: String, expectedCode: Int, status: String?) {
    return cluster {
        assertWithRetryIgnoringExceptions {
            command { multipleFlowStatus(holdingId, status) }
            timeout(RETRY_TIMEOUT)
            condition {
                it.code == expectedCode
            }
        }
    }
}

@Suppress("unused")
fun getFlowClasses(
    holdingId: String
) = DEFAULT_CLUSTER.getFlowClasses(holdingId)

fun ClusterInfo.getFlowClasses(holdingId: String): List<String> {
    return cluster {
        val vNodeJson = assertWithRetryIgnoringExceptions {
            timeout(RETRY_TIMEOUT)
            command { runnableFlowClasses(holdingId) }
            condition { it.code == 200 }
            failMessage("Failed to get flows for holdingId '$holdingId'")
        }.toJson()

        vNodeJson["flowClassNames"].map { it.textValue() }
    }
}

class RestSmokeTestInput {
    var command: String? = null
    var data: Map<String, String>? = null
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
    override fun toString(): String {
        return "FlowError(type=$type, message=$message)"
    }
}

data class FlowResult(
    val flowStatus: String?,
    val json: JsonNode?,
    val flowError: FlowError?
)

class TestRequestIdGenerator(testName: String) {
    companion object {

        val logger = LoggerFactory.getLogger(this::class.java)

        private fun getNameFromTestInfo(testInfo: TestInfo): String {
            val parameterNumber = Regex("^\\[(\\d+)\\]").find(testInfo.displayName)?.groups?.get(1)?.value
            val testName = if (!parameterNumber.isNullOrBlank()) {
                "${testInfo.testMethod.get().name}-param_$parameterNumber"
            } else {
                testInfo.displayName.removeSuffix("(TestInfo)")
            }
            return if (testName.length > 235) {
                val name = "${testName.takeLast(150)}-${UUID.randomUUID()}"
                logger.warn("Test exceeding 235 characters, shortening to $name")
                name

            } else {
                testName
            }
        }
    }

    constructor(testInfo: TestInfo) : this(getNameFromTestInfo(testInfo))

    private val baseName: String = Regex("[^-._A-Za-z0-9]").replace(testName, "_")
    private val guid = UUID.randomUUID()
    private var count = 0

    val nextId: String
        get() {
            return "$baseName-$guid-${count++}"
        }
}