package net.corda.applications.workers.smoketest.virtualnode.alternate

// import net.corda.applications.workers.smoketest.toJson
// import org.junit.jupiter.api.MethodOrderer
// import org.junit.jupiter.api.TestMethodOrder
// import java.time.temporal.ChronoUnit
import com.fasterxml.jackson.databind.JsonNode
import net.corda.applications.workers.smoketest.CLUSTER_URI
import net.corda.applications.workers.smoketest.GROUP_ID
import net.corda.applications.workers.smoketest.PASSWORD
import net.corda.applications.workers.smoketest.TEST_CPB_LOCATION
import net.corda.applications.workers.smoketest.TEST_CPI_NAME
import net.corda.applications.workers.smoketest.USERNAME
import net.corda.applications.workers.smoketest.awaitRpcFlowFinished
import net.corda.applications.workers.smoketest.getHoldingIdShortHash
import net.corda.applications.workers.smoketest.startRpcFlow
import net.corda.applications.workers.smoketest.truncateLongHash
import net.corda.applications.workers.smoketest.virtualnode.helpers.ClusterBuilder
import net.corda.applications.workers.smoketest.virtualnode.helpers.assertWithRetry
import net.corda.applications.workers.smoketest.virtualnode.helpers.cluster
import net.corda.httprpc.ResponseCode.CONFLICT
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

const val CODESIGNER_CERT = "/cordadevcodesign.pem"

// @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VirtualNodeRpcTest {
    companion object {
        // Some simple test failure messages
        private const val ERROR_CPI_NOT_UPLOADED =
            "CPI has not been uploaded during this run - this test needs to be run on a clean cluster."
        private const val ERROR_IS_CLUSTER_RUNNING = "Initial upload failed - is the cluster running?"
        private const val ERROR_HOLDING_ID =
            "Holding id could not be created - this test needs to be run on a clean cluster."

        // Server side messages
        private const val EXPECTED_ERROR_NO_GROUP_POLICY = "CPI is missing a group policy file"

        private val testRunUniqueId = UUID.randomUUID()
        private val aliceX500 = "CN=Alice-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private val aliceHoldingId: String = getHoldingIdShortHash(aliceX500, GROUP_ID)
        private val staticMemberList = listOf(
            aliceX500
        )

        @BeforeAll
        @JvmStatic
        fun `import codesigner certificate`() {
            cluster {
                endpoint(CLUSTER_URI, USERNAME, PASSWORD)
                assertWithRetry {
                    // Certificate upload can be slow in the combined worker, especially after it has just started up.
                    timeout(Duration.ofSeconds(100))
                    interval(Duration.ofSeconds(1))
                    command { importCertificate(CODESIGNER_CERT, "code-signer", "cordadev") }
                    condition { it.code == 204 }
                }
            }
        }
    }

    fun generateCpiName(): String = "${TEST_CPI_NAME}_${testRunUniqueId}_${UUID.randomUUID()}"

    fun ClusterBuilder.uploadCpiAndWait(cpiName: String = generateCpiName(), groupId: String = GROUP_ID): JsonNode {
        val requestId = cpiUpload(TEST_CPB_LOCATION, groupId, staticMemberList, cpiName)
            .let { it.toJson()["id"].textValue() }
        assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty
        return assertWithRetry {
            // CPI upload can be slow in the combined worker, especially after it has just started up.
            timeout(Duration.ofSeconds(100))
            interval(Duration.ofSeconds(2))
            command { cpiStatus(requestId) }
            condition {
                it.code == 200 && it.toJson()["status"].textValue() == "OK"
            }
            immediateFailCondition {
                it.code == CONFLICT.statusCode &&
                        null != it.toJson()["details"] &&
                        it.toJson()["details"]["code"].textValue().equals(CONFLICT.toString()) &&
                        null != it.toJson()["title"] &&
                        it.toJson()["title"].textValue().contains("already uploaded")
            }
        }.toJson()
    }

    /**
     * This test, uploads a CPI into the system.
     */
    @Test
    fun `can upload CPI`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
            val cpiName = generateCpiName()

            // BUG:  returning "OK" feels 'weakly' typed
            val json = this.uploadCpiAndWait(cpiName)

            val cpiHash = json["cpiFileChecksum"].textValue()
            assertThat(cpiHash).isNotNull.isNotEmpty

            // Capture the cpiHash from the cpi status upload
            // We probably want more tests like this that enforce "expectations" on the API.
            assertThat(cpiHash!!.length)
                .withFailMessage("Short code length of wrong size - likely this test needs fixing")
                .isEqualTo(12)

            val actualChecksum = getCpiChecksum(cpiName)

            assertThat(actualChecksum).isNotNull.isNotEmpty

            assertThat(cpiHash).withFailMessage(ERROR_CPI_NOT_UPLOADED).isNotNull

            assertThat(actualChecksum).isEqualTo(cpiHash)
        }
    }

    /**
     * Runs second to ensure that we reject this with a correct message
     */
    @Test
    fun `cannot upload CPI without group policy file aka CPB`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            val requestId = cpbUpload(TEST_CPB_LOCATION).let { it.toJson()["id"].textValue() }
            assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

            assertWithRetry {
                command { cpiStatus(requestId) }
                condition {
                    try {
                        if (it.code == 400) {
                            val json = it.toJson()["details"]
                            json.has("errorMessage") &&
                                    json["errorMessage"].textValue() == EXPECTED_ERROR_NO_GROUP_POLICY
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        println("Failed, repsonse: $it")
                        false
                    }
                }
            }
        }
    }

    @Test
    fun `cannot upload same CPI`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
            val cpiName = generateCpiName()
            this.uploadCpiAndWait(cpiName)

            val requestId = cpiUpload(TEST_CPB_LOCATION, GROUP_ID, staticMemberList, cpiName)
                .let { it.toJson()["id"].textValue() }
            assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

            assertWithRetry {
                command { cpiStatus(requestId) }
                condition { it.code == 409 }
            }
        }
    }

    @Test
    fun `cannot upload same CPI with different groupId`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
            val cpiName = generateCpiName()
            this.uploadCpiAndWait(cpiName)
            val requestId = cpiUpload(
                TEST_CPB_LOCATION,
                "8c5d6948-e17b-44e7-9d1c-fa4a3f667cad",
                staticMemberList,
                cpiName
            ).let { it.toJson()["id"].textValue() }
            assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

            assertWithRetry {
                command { cpiStatus(requestId) }
                condition { it.code == 409 }
            }
        }
    }

    @Test
    fun `can upload different CPI with same groupId`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
            val otherCpiName = generateCpiName()
            this.uploadCpiAndWait(generateCpiName(), "8c5d6948-e17b-44e7-9d1c-fa4a3f667cad")
            val json = this.uploadCpiAndWait(otherCpiName, "8c5d6948-e17b-44e7-9d1c-fa4a3f667cad")

            val cpiHash = json["cpiFileChecksum"].textValue()
            assertThat(cpiHash).isNotNull.isNotEmpty
            val actualChecksum = getCpiChecksum(otherCpiName)
            assertThat(actualChecksum).isNotNull.isNotEmpty
            assertThat(cpiHash).withFailMessage(ERROR_CPI_NOT_UPLOADED).isNotNull
            assertThat(actualChecksum).isEqualTo(cpiHash)
        }
    }

    @Test
    fun `list cpis`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
            this.uploadCpiAndWait(generateCpiName())
            val json = assertWithRetry {
                command { cpiList() }
                condition { it.code == 200 }
            }.toJson()

            assertThat(json["cpis"].size()).isGreaterThan(0)
        }
    }
//
//    @Test
//    @Order(37)
//    fun `list cpis and check group id matches value in group policy file`() {
//        cluster {
//            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
//
//            val json = cpiList().toJson()
//            val cpiJson = json["cpis"].first { it["id"]["cpiName"].textValue() == cpiName}
//
//            val groupPolicyJson = cpiJson["groupPolicy"].textValue().toJson()
//            assertThat(groupPolicyJson["groupId"].textValue()).isEqualTo(GROUP_ID)
//        }
//    }
//
//    @Test
//    @Order(40)
//    fun `can create virtual node with holding id and CPI`() {
//        cluster {
//            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
//            val hash = getCpiChecksum(cpiName)
//
//            val vNodeJson = assertWithRetry {
//                command { vNodeCreate(hash, aliceX500) }
//                condition { it.code == 200 }
//                failMessage(ERROR_HOLDING_ID)
//            }.toJson()
//
//            assertThat(vNodeJson["holdingIdentity"]["shortHash"].textValue()).isNotNull.isNotEmpty
//        }
//    }
//
//    @Test
//    @Order(50)
//    fun `cannot create duplicate virtual node`() {
//        cluster {
//            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
//            val hash = getCpiChecksum(cpiName)
//
//            assertWithRetry {
//                command { vNodeCreate(hash, aliceX500) }
//                condition { it.code == 409 }
//            }
//        }
//    }
//
//    @Test
//    @Order(60)
//    fun `list virtual nodes`() {
//        cluster {
//            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
//
//            assertWithRetry {
//                timeout(Duration.of(30, ChronoUnit.SECONDS))
//                command { vNodeList() }
//                condition { response ->
//                    val nodes = vNodeList().toJson()["virtualNodes"].map {
//                        it["holdingIdentity"]["x500Name"].textValue()
//                    }
//                    response.code == 200 && nodes.contains(aliceX500)
//                }
//            }
//        }
//    }
//
//    @Test
//    @Order(61)
//    fun `set virtual node state`() {
//        cluster {
//            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
//            val vnodesWithStates: List<Pair<String, String>> = vNodeList().toJson()["virtualNodes"].map {
//                it["holdingIdentity"]["shortHash"].textValue() to it["state"].textValue()
//            }
//
//            val (vnodeId, oldState) = vnodesWithStates.last()
//            val newState = "IN_MAINTENANCE"
//
//            updateVirtualNodeState(vnodeId, newState)
//
//            assertWithRetry {
//                timeout(Duration.of(60, ChronoUnit.SECONDS))
//                command { vNodeList() }
//                condition {
//                    it.code == 200 &&
//                            it.toJson()["virtualNodes"].single { virtualNode ->
//                                virtualNode["holdingIdentity"]["shortHash"].textValue() == vnodeId
//                            }["state"].textValue() == newState
//                }
//            }
//
//            updateVirtualNodeState(vnodeId, oldState)
//
//            assertWithRetry {
//                timeout(Duration.of(60, ChronoUnit.SECONDS))
//                command { vNodeList() }
//                condition {
//                    it.code == 200 &&
//                            it.toJson()["virtualNodes"].single { virtualNode ->
//                                virtualNode["holdingIdentity"]["shortHash"].textValue() == vnodeId
//                            }["state"].textValue() == oldState
//                }
//            }
//        }
//    }
//
//    @Test
//    @Order(65)
//    fun `cpi status returns 400 for unknown request id`() {
//        cluster {
//            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
//            assertWithRetry {
//                command { cpiStatus("THIS_WILL_NEVER_BE_A_CPI_STATUS") }
//                condition { it.code == 400 }
//            }
//        }
//    }
//
//    @Disabled("Pending https://r3-cev.atlassian.net/browse/CORE-7629")
//    @Test
//    @Order(80)
//    fun `can force upload same CPI`() {
//        cluster {
//            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
//
//            // Note CPI/CPK timestamp
//            val initialCpkTimeStamp = getCpkTimestamp(cpiName)
//
//            // Perform force upload of the CPI
//            val requestId = forceCpiUpload(TEST_CPB_LOCATION, GROUP_ID, staticMemberList, cpiName).let { it.toJson()["id"].textValue() }
//            assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty
//
//            // BUG:  returning "OK" feels 'weakly' typed
//            assertWithRetry {
//                command { cpiStatus(requestId) }
//                condition { it.code == 200 && it.toJson()["status"].textValue() == "OK" }
//            }
//
//            // Check that timestamp for CPK been updated
//            // Cannot use `assertWithRetry` as there is a strict type `Instant`
//            // Allow ample time for CPI upload to be propagated through the system
//            eventually(Duration.ofSeconds(100)) {
//                assertThat(getCpkTimestamp(cpiName)).isAfter(initialCpkTimeStamp)
//            }
//        }
//    }

    // This test has no effect without forceUploading
    @Disabled("Pending https://r3-cev.atlassian.net/browse/CORE-7629")
    @Test
    @Order(81)
    fun `can run the uploaded CPI`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            runReturnAStringFlow("original-cpi")
        }
    }

//    @Test
//    @Order(82)
//    fun `persist dog`() {
//        cluster {
//            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
//
//            runSimplePersistenceCheckFlow("Could persist dog")
//        }
//    }

//    @Disabled("Pending https://r3-cev.atlassian.net/browse/CORE-7629")
//    @Test
//    @Order(90)
//    fun `can force upload CPI with same name and version but a change to ReturnAStringFlow`() {
//        cluster {
//            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
//
//            val initialCpkTimeStamp = getCpkTimestamp(cpiName)
//
//            val requestId = forceCpiUpload(CACHE_INVALIDATION_TEST_CPB, GROUP_ID, staticMemberList, cpiName)
//                .let { it.toJson()["id"].textValue() }
//            assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty
//
//            assertWithRetry {
//                command { cpiStatus(requestId) }
//                condition { it.code == 200 && it.toJson()["status"].textValue() == "OK" }
//            }
//
//            eventually(Duration.ofSeconds(120)) {
//                assertThat(getCpkTimestamp(cpiName)).isAfter(initialCpkTimeStamp)
//            }
//        }
//    }

//    @Disabled("Pending https://r3-cev.atlassian.net/browse/CORE-7629")
//    @Test
//    @Order(91)
//    fun `can run the force-uploaded CPI with a change to ReturnAStringFlow`() {
//        cluster {
//            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
//
//            runReturnAStringFlow("force-uploaded-cpi")
//        }
//    }
//
//    @Disabled("Pending https://r3-cev.atlassian.net/browse/CORE-7629")
//    @Test
//    @Order(92)
//    fun `Can sync DB and persist fish`() {
//        cluster {
//            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
//            // Status 204 indicates a non-error but no response data
//            assertThat(syncVirtualNode(aliceHoldingId).code).isEqualTo(204)
//
//            runSimplePersistenceCheckFlow("Could persist Floaty")
//        }
//    }
//
//    @Disabled("Pending https://r3-cev.atlassian.net/browse/CORE-7629")
//    @Test
//    @Order(100)
//    fun `can force upload the original CPI check that the original ReturnAStringFlow is available on the flow sandbox cache`() {
//        cluster {
//            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
//
//            val initialCpkTimeStamp = getCpkTimestamp(cpiName)
//
//            val requestId = forceCpiUpload(TEST_CPB_LOCATION, GROUP_ID, staticMemberList, cpiName)
//                .let { it.toJson()["id"].textValue() }
//            assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty
//
//            assertWithRetry {
//                command { cpiStatus(requestId) }
//                condition { it.code == 200 && it.toJson()["status"].textValue() == "OK" }
//            }
//
//            eventually(Duration.ofSeconds(100)) {
//                assertThat(getCpkTimestamp(cpiName)).isAfter(initialCpkTimeStamp)
//            }
//
//            runReturnAStringFlow("original-cpi")
//        }
//    }
//
//    @Disabled("Pending https://r3-cev.atlassian.net/browse/CORE-7629")
//    @Test
//    @Order(101)
//    fun `Can sync DB again and persist dog`() {
//        cluster {
//            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
//            assertThat(syncVirtualNode(aliceHoldingId).code).isEqualTo(204)
//
//            runSimplePersistenceCheckFlow("Could persist dog")
//        }
//    }

    private fun runReturnAStringFlow(expectedResult: String) {
        val className = "net.cordapp.testing.smoketests.virtualnode.ReturnAStringFlow"

        val requestId = startRpcFlow(aliceHoldingId, emptyMap(), className)

        val flowStatus = awaitRpcFlowFinished(aliceHoldingId, requestId)

        assertThat(flowStatus.flowResult).isEqualTo(expectedResult)
    }

    private fun runSimplePersistenceCheckFlow(expectedResult: String) {
        val className = "net.cordapp.testing.smoketests.virtualnode.SimplePersistenceCheckFlow"

        val requestId = startRpcFlow(aliceHoldingId, emptyMap(), className)

        val flowStatus = awaitRpcFlowFinished(aliceHoldingId, requestId)

        assertThat(flowStatus.flowResult).isEqualTo(expectedResult)
    }

    private fun ClusterBuilder.getCpkTimestamp(cpiName: String): Instant {
        val cpis = cpiList().toJson()["cpis"]
        val cpiJson = cpis.toList().first { it["id"]["cpiName"].textValue() == cpiName }
        val cpksJson = cpiJson["cpks"].toList()
        return cpksJson.first()["timestamp"].toInstant()
    }

    private fun JsonNode.toInstant(): Instant {
        return Instant.parse(this.asText())
    }

    private fun ClusterBuilder.getCpiChecksum(cpiName: String): String {
        val cpiFileChecksum = eventually {
            val cpis = cpiList().toJson()["cpis"]
            val cpiJson = cpis.toList().find { it["id"]["cpiName"].textValue() == cpiName }
            assertNotNull(cpiJson, "Cpi with name $cpiName not yet found in cpi list.")
            truncateLongHash(cpiJson!!["cpiFileChecksum"].textValue())
        }
        return cpiFileChecksum
    }
}
    