package net.corda.applications.workers.smoketest.virtualnode

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import net.corda.applications.workers.smoketest.CLUSTER_URI
import net.corda.applications.workers.smoketest.CPI_NAME
import net.corda.applications.workers.smoketest.GROUP_ID
import net.corda.applications.workers.smoketest.PASSWORD
import net.corda.applications.workers.smoketest.USERNAME
import net.corda.applications.workers.smoketest.X500_ALICE
import net.corda.applications.workers.smoketest.awaitRpcFlowFinished
import net.corda.applications.workers.smoketest.getHoldingIdShortHash
import net.corda.applications.workers.smoketest.startRpcFlow
import net.corda.applications.workers.smoketest.truncateLongHash
import net.corda.applications.workers.smoketest.virtualnode.helpers.ClusterBuilder
import net.corda.applications.workers.smoketest.virtualnode.helpers.SimpleResponse
import net.corda.applications.workers.smoketest.virtualnode.helpers.assertWithRetry
import net.corda.applications.workers.smoketest.virtualnode.helpers.cluster
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

// The CPB we're using in this test
const val TEST_CPB = "/META-INF/flow-worker-dev.cpb"
const val CACHE_INVALIDATION_TEST_CPB = "/META-INF/cache-invalidation-testing/flow-worker-dev.cpb"

fun SimpleResponse.toJson(): JsonNode = ObjectMapper().readTree(this.body)!!
/**
 * Any 'unordered' tests are run *last*
 */
@Order(10)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class VirtualNodeRpcTest {
    companion object {
        // Some simple test failure messages
        private const val ERROR_CPI_NOT_UPLOADED =
            "CPI has not been uploaded during this run - this test needs to be run on a clean cluster."
        private const val ERROR_IS_CLUSTER_RUNNING = "Initial upload failed - is the cluster running?"
        private const val ERROR_HOLDING_ID =
            "Holding id could not be created - this test needs to be run on a clean cluster."

        // Server side messages
        // BUG: These server-side messages can change arbitrarily - not ideal at the moment, but we return all errors with code '500' and no other info.
        private const val EXPECTED_ERROR_NO_GROUP_POLICY = "CPI is missing a group policy file"
        private const val EXPECTED_ERROR_ALREADY_UPLOADED = "CPI already uploaded with groupId"

        private val aliceHoldingId: String = getHoldingIdShortHash(X500_ALICE, GROUP_ID)
    }

    /**
     * As long as no-one assigns an order lower than this, this test runs first, and all others, after, which is fine.
     *
     * This *first* test, uploads a CPI into the system.  It must pass.
     */
    @Test
    @Order(10)
    fun `can upload CPI`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            val requestId = cpiUpload(TEST_CPB, GROUP_ID).let { it.toJson()["id"].textValue() }
            assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

            // BUG:  returning "OK" feels 'weakly' typed
            val json = assertWithRetry {
                // CPI upload can be slow in the combined worker, especially after it has just started up.
                timeout(Duration.ofSeconds(100))
                interval(Duration.ofSeconds(2))
                command { cpiStatus(requestId) }
                condition {
                    it.code == 200 && it.toJson()["status"].textValue() == "OK" }
                immediateFailCondition {
                    it.code == 400
                            && null != it.toJson()["details"]
                            && it.toJson()["details"]["errorMessage"].textValue()
                        .startsWith("CPI already uploaded")
                }
            }.toJson()

            val cpiHash = json["cpiFileChecksum"].textValue()
            assertThat(cpiHash).isNotNull.isNotEmpty

            // Capture the cpiHash from the cpi status upload
            // We probably want more tests like this that enforce "expectations" on the API.
            assertThat(cpiHash!!.length)
                .withFailMessage("Short code length of wrong size - likely this test needs fixing")
                .isEqualTo(12)

            val actualChecksum = getCpiChecksum(CPI_NAME)

            assertThat(actualChecksum).isNotNull.isNotEmpty

            assertThat(cpiHash).withFailMessage(ERROR_CPI_NOT_UPLOADED).isNotNull

            assertThat(actualChecksum).isEqualTo(cpiHash)
        }
    }

    /**
     * Runs second to ensure that we reject this with a correct message
     */
    @Test
    @Order(20)
    fun `cannot upload CPI without group policy file aka CPB`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            val requestId = cpbUpload(TEST_CPB).let { it.toJson()["id"].textValue() }
            assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

            assertWithRetry {
                command { cpiStatus(requestId) }
                condition {
                    try {
                        if(it.code == 400) {
                            val json = it.toJson()["details"]
                            json.has("errorMessage")
                                    && json["errorMessage"].textValue() == EXPECTED_ERROR_NO_GROUP_POLICY
                        } else {
                            false
                        }
                    }
                    catch (e: Exception) {
                        println("Failed, repsonse: $it")
                        false
                    }
                }
            }
        }
    }

    @Test
    @Order(30)
    fun `cannot upload same CPI`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
            val requestId = cpiUpload(TEST_CPB, GROUP_ID).let { it.toJson()["id"].textValue() }
            assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

            assertWithRetry {
                command { cpiStatus(requestId) }
                condition { it.code == 409 }
            }
        }
    }

    @Test
    @Order(33)
    fun `list cpis`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            val json = assertWithRetry {
                command { cpiList() }
                condition { it.code == 200 }
            }.toJson()

            assertThat(json["cpis"].size()).isGreaterThan(0)
        }
    }

    @Test
    @Order(37)
    fun `list cpis and check group id matches value in group policy file`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            val json = cpiList().toJson()
            val cpiJson = json["cpis"].first()

            val groupPolicyJson = cpiJson["groupPolicy"].textValue().toJson()
            assertThat(groupPolicyJson["groupId"].textValue()).isEqualTo(GROUP_ID)
        }
    }

    @Test
    @Order(40)
    fun `can create virtual node with holding id and CPI`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
            val hash = getCpiChecksum(CPI_NAME)

            val vNodeJson = assertWithRetry {
                command { vNodeCreate(hash, X500_ALICE) }
                condition { it.code == 200 }
                failMessage(ERROR_HOLDING_ID)
            }.toJson()

            assertThat(vNodeJson["holdingIdentity"]["shortHash"].textValue()).isNotNull.isNotEmpty
        }
    }

    @Test
    @Order(50)
    fun `cannot create duplicate virtual node`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
            val hash = getCpiChecksum(CPI_NAME)

            assertWithRetry {
                command { vNodeCreate(hash, X500_ALICE) }
                condition { it.code == 500 }
            }
        }
    }

    @Test
    @Order(60)
    fun `list virtual nodes`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
            val nodes = vNodeList().toJson()["virtualNodes"].map {
                it["holdingIdentity"]["x500Name"].textValue()
            }

            assertThat(nodes).contains(X500_ALICE)
        }
    }

    @Test
    @Order(61)
    fun `set virtual node state`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
            val states = vNodeList().toJson()["virtualNodes"].map {
                it["holdingIdentity"]["shortHash"].textValue() to it["state"].textValue()
            }

            val vnode = states.last()
            val oldState = vnode.second
            val newState = "IN_MAINTENANCE"

            updateVirtualNodeState(vnode.first, newState)

            assertWithRetry {
                timeout(Duration.of(10, ChronoUnit.SECONDS))
                command { vNodeList() }
                condition {
                    it.code == 200 &&
                            it.toJson()["virtualNodes"].single { virtualNode ->
                                virtualNode["holdingIdentity"]["shortHash"].textValue() == vnode.first
                            }["state"].textValue() == newState
                }
            }

            updateVirtualNodeState(vnode.first, oldState)

            assertWithRetry {
                timeout(Duration.of(10, ChronoUnit.SECONDS))
                command { vNodeList() }
                condition {
                    it.code == 200 &&
                            it.toJson()["virtualNodes"].single { virtualNode ->
                                virtualNode["holdingIdentity"]["shortHash"].textValue() == vnode.first
                            }["state"].textValue() == oldState
                }
            }
        }
    }

    @Test
    @Order(65)
    fun `cpi status returns 400 for unknown request id`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
            assertWithRetry {
                command { cpiStatus("THIS_WILL_NEVER_BE_A_CPI_STATUS") }
                condition { it.code == 400 }
            }
        }
    }

    @Test
    @Order(80)
    fun `can force upload same CPI`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            // Note CPI/CPK timestamp
            val initialCpkTimeStamp = getCpkTimestamp()

            // Perform force upload of the CPI
            val requestId = forceCpiUpload(TEST_CPB, GROUP_ID).let { it.toJson()["id"].textValue() }
            assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

            // BUG:  returning "OK" feels 'weakly' typed
            assertWithRetry {
                command { cpiStatus(requestId) }
                condition { it.code == 200 && it.toJson()["status"].textValue() == "OK" }
            }

            // Check that timestamp for CPK been updated
            // Cannot use `assertWithRetry` as there is a strict type `Instant`
            // Allow ample time for CPI upload to be propagated through the system
            eventually(Duration.ofSeconds(100)) {
                assertThat(getCpkTimestamp()).isAfter(initialCpkTimeStamp)
            }
        }
    }

    @Test
    @Order(81)
    fun `can run the uploaded CPI`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            runReturnAStringFlow("original-cpi")
        }
    }

    @Test
    @Order(90)
    fun `can force upload CPI with same name and version but a change to ReturnAStringFlow`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            val initialCpkTimeStamp = getCpkTimestamp()

            val requestId = forceCpiUpload(CACHE_INVALIDATION_TEST_CPB, GROUP_ID).let { it.toJson()["id"].textValue() }
            assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

            assertWithRetry {
                command { cpiStatus(requestId) }
                condition { it.code == 200 && it.toJson()["status"].textValue() == "OK" }
            }

            eventually(Duration.ofSeconds(100)) {
                assertThat(getCpkTimestamp()).isAfter(initialCpkTimeStamp)
            }
        }
    }

    @Test
    @Order(91)
    fun `can run the force-uploaded CPI with a change to ReturnAStringFlow`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            runReturnAStringFlow("force-uploaded-cpi")
        }
    }

    @Test
    @Order(92)
    fun `can force upload the original CPI check that the original ReturnAStringFlow is available on the flow sandbox cache`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            val initialCpkTimeStamp = getCpkTimestamp()

            val requestId = forceCpiUpload(TEST_CPB, GROUP_ID).let { it.toJson()["id"].textValue() }
            assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

            assertWithRetry {
                command { cpiStatus(requestId) }
                condition { it.code == 200 && it.toJson()["status"].textValue() == "OK" }
            }

            eventually(Duration.ofSeconds(100)) {
                assertThat(getCpkTimestamp()).isAfter(initialCpkTimeStamp)
            }

            runReturnAStringFlow("original-cpi")
        }
    }

    private fun runReturnAStringFlow(expectedResult: String) {
        val className = "net.cordapp.flowworker.development.flows.ReturnAStringFlow"

        val requestId = startRpcFlow(aliceHoldingId, emptyMap(), className)

        val flowStatus = awaitRpcFlowFinished(aliceHoldingId, requestId)

        assertThat(flowStatus.flowResult).isEqualTo(expectedResult)
    }

    private fun ClusterBuilder.getCpkTimestamp(): Instant {
        val cpis = cpiList().toJson()["cpis"]
        val cpiJson = cpis.toList().first { it["id"]["cpiName"].textValue() == CPI_NAME }
        val cpksJson = cpiJson["cpks"].toList()
        return cpksJson.first()["timestamp"].toInstant()
    }

    private fun JsonNode.toInstant(): Instant {
        return Instant.parse(this.asText())
    }

    fun ClusterBuilder.getCpiChecksum(cpiName: String): String {
        val cpis = cpiList().toJson()["cpis"]
        val cpiJson = cpis.toList().first { it["id"]["cpiName"].textValue() == cpiName }
        return truncateLongHash(cpiJson["cpiFileChecksum"].textValue())
    }

    private fun String.toJson(): JsonNode = ObjectMapper().readTree(this)
}
