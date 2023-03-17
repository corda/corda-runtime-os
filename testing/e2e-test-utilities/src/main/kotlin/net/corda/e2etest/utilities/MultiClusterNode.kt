package net.corda.e2etest.utilities

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.contains
import net.corda.rest.ResponseCode
import net.corda.test.util.eventually
import net.corda.v5.base.types.MemberX500Name
import org.apache.commons.text.StringEscapeUtils
import org.assertj.core.api.Assertions
import java.net.URI
import java.time.Duration
import java.util.*

class MultiClusterNode(private val clusterUri: URI) {
    /**
     * Transform a Corda Package Bundle (CPB) into a Corda Package Installer (CPI) by adding a group policy file and upload
     * the resulting CPI to the system if it doesn't already exist.
     */
    fun conditionallyUploadCordaPackage(
        name: String,
        cpb: String,
        groupPolicy: String
    ) = conditionallyUploadCordaPackage(name) {
        cpiUpload(cpb, groupPolicy, name)
    }

    fun conditionallyUploadCordaPackage(
        name: String,
        cpiUpload: ClusterBuilder.() -> SimpleResponse
    ) {
        return cluster {
            endpoint(
                clusterUri,
                USERNAME,
                PASSWORD
            )

            if (getExistingCpi(name) == null) {
                val responseStatusId = cpiUpload().run {
                    Assertions.assertThat(code).isEqualTo(ResponseCode.OK.statusCode)
                    Assertions.assertThat(toJson()["id"].textValue()).isNotEmpty
                    toJson()["id"].textValue()
                }

                assertWithRetry {
                    timeout(Duration.ofSeconds(100))
                    interval(Duration.ofSeconds(2))
                    command { cpiStatus(responseStatusId) }
                    condition {
                        it.code == ResponseCode.OK.statusCode
                                && it.toJson()["status"].textValue() == ResponseCode.OK.toString()
                    }
                }
            }
        }
    }

    fun getOrCreateVirtualNodeFor(
        x500: String,
        cpiName: String
    ): String {
        return cluster {
            endpoint(
                clusterUri,
                USERNAME,
                PASSWORD
            )
            // be a bit patient for the CPI info to get there in case it has just been uploaded
            val json = eventually(
                duration = Duration.ofSeconds(30)
            ) {
                getExistingCpi(cpiName).apply {
                    Assertions.assertThat(this).isNotNull
                }!!
            }
            val hash = truncateLongHash(json["cpiFileChecksum"].textValue())

            val vNodesJson = assertWithRetry {
                command { vNodeList() }
                condition { it.code == 200 }
                failMessage("Failed to retrieve virtual nodes")
            }.toJson()

            val normalizedX500 = MemberX500Name.parse(x500).toString()

            if (vNodesJson.findValuesAsText("x500Name").contains(normalizedX500)) {
                vNodeList().toJson()["virtualNodes"].toList().first {
                    it["holdingIdentity"]["x500Name"].textValue() == normalizedX500
                }["holdingIdentity"]["shortHash"].textValue()
            } else {
                val createVNodeRequest = assertWithRetry {
                    command { vNodeCreate(hash, x500) }
                    condition { it.code == 202 }
                    failMessage("Failed to create the virtual node for '$x500'")
                }.toJson()

                val holdingId = createVNodeRequest["requestId"].textValue()

                // Wait for the vNode creation to propagate through the system before moving on
                eventually(duration = Duration.ofSeconds(30)) {
                    Assertions.assertThat(getVNode(holdingId).code).isNotEqualTo(404)
                }

                holdingId
            }
        }
    }

    fun addSoftHsmFor(holdingId: String, category: String) {
        return cluster {
            endpoint(
                clusterUri,
                USERNAME,
                PASSWORD
            )
            assertWithRetry {
                timeout(Duration.ofSeconds(5))
                command { addSoftHsmToVNode(holdingId, category) }
                condition { it.code == 200 }
                failMessage("Failed to add SoftHSM for holding id '$holdingId'")
            }
        }
    }

    fun getKeyFor(tenantId: String, alias: String, category: String, scheme: String): String {
        return cluster {
            endpoint(
                clusterUri,
                USERNAME,
                PASSWORD
            )
            val keyId = assertWithRetry {
                command { createKey(tenantId, alias, category, scheme) }
                condition { it.code == 200 }
                failMessage("Failed to create key for holding id '$tenantId'")
            }.toJson()
            assertWithRetry {
                command { getKey(tenantId, keyId["id"].textValue()) }
                condition { it.code == 200 }
                failMessage("Failed to get key for holding id '$tenantId' and key id '$keyId'")
            }
            keyId["id"].textValue()
        }
    }

    fun createKeyFor(tenantId: String, alias: String, category: String, scheme: String): String {
        return cluster {
            endpoint(
                clusterUri,
                USERNAME,
                PASSWORD
            )
            val keyId = assertWithRetry {
                command { createKey(tenantId, alias, category, scheme) }
                condition { it.code == 200 }
                failMessage("Failed to create key for holding id '$tenantId'")
            }.toJson()
            assertWithRetry {
                command { getKey(tenantId, keyId["id"].textValue()) }
                condition { it.code == 200 }
                failMessage("Failed to get key for holding id '$tenantId' and key id '$keyId'")
            }
            keyId["id"].textValue()
        }
    }

    fun keyExists(
        tenantId: String,
        alias: String? = null,
        category: String? = null,
        ids: List<String>? = null
    ): Boolean {
        return cluster {
            endpoint(clusterUri, USERNAME, PASSWORD)
            val result = getKey(tenantId, category, alias, ids)
            result.code == ResponseCode.OK.statusCode && result.toJson().fieldNames().hasNext()
        }
    }

    fun startRpcFlow(holdingId: String, args: Map<String, Any>, flowName: String, expectedCode: Int = 202): String {
        return cluster {
            endpoint(
                clusterUri,
                USERNAME,
                PASSWORD
            )

            val requestId = UUID.randomUUID().toString()

            assertWithRetry {
                command {
                    flowStart(
                        holdingId,
                        requestId,
                        flowName, StringEscapeUtils.escapeJson(ObjectMapper().writeValueAsString(args))
                    )
                }
                condition { it.code == expectedCode }
            }

            requestId
        }
    }

    fun awaitRpcFlowFinished(holdingId: String, requestId: String): FlowStatus {
        return cluster {
            endpoint(
                clusterUri,
                USERNAME,
                PASSWORD
            )

            ObjectMapper().readValue(
                assertWithRetry {
                    command { flowStatus(holdingId, requestId) }
                    //CORE-6118 - tmp increase this timeout to a large number to allow tests to pass while slow flow
                    // sessions are investigated
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

    fun getFlowStatus(holdingId: String, requestId: String, expectedCode: Int): FlowStatus {
        return  cluster {
            endpoint(
                clusterUri,
                USERNAME,
                PASSWORD
            )

            ObjectMapper().readValue(
                assertWithRetry {
                    command { flowStatus(holdingId, requestId) }
                    timeout(Duration.ofMinutes(6))
                    condition {
                        it.code == expectedCode
                    }
                }.body, FlowStatus::class.java
            )
        }
    }

    fun awaitMultipleRpcFlowFinished(holdingId: String, expectedFlowCount: Int) {
        return cluster {
            endpoint(
                clusterUri,
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
}