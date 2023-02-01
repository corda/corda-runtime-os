package net.corda.applications.workers.smoketest.virtualnode

import net.corda.applications.workers.smoketest.CACHE_INVALIDATION_TEST_CPB
import net.corda.applications.workers.smoketest.TEST_CPB_LOCATION
import net.corda.applications.workers.smoketest.TEST_CPI_NAME
import net.corda.e2etest.utilities.CLUSTER_URI
import net.corda.e2etest.utilities.CODE_SIGNER_CERT
import net.corda.e2etest.utilities.ClusterBuilder
import net.corda.e2etest.utilities.GROUP_ID
import net.corda.e2etest.utilities.PASSWORD
import net.corda.e2etest.utilities.USERNAME
import net.corda.e2etest.utilities.assertWithRetry
import net.corda.e2etest.utilities.awaitRpcFlowFinished
import net.corda.e2etest.utilities.cluster
import net.corda.e2etest.utilities.getHoldingIdShortHash
import net.corda.e2etest.utilities.startRpcFlow
import net.corda.e2etest.utilities.toJson
import net.corda.e2etest.utilities.truncateLongHash
import net.corda.httprpc.ResponseCode.CONFLICT
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.UUID
import net.corda.applications.workers.smoketest.TEST_CPB_WITHOUT_CHANGELOGS_LOCATION
import net.corda.applications.workers.smoketest.VNODE_UPGRADE_TEST_CPI_NAME
import net.corda.applications.workers.smoketest.VNODE_UPGRADE_TEST_CPI_V1
import net.corda.applications.workers.smoketest.VNODE_UPGRADE_TEST_CPI_V2

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
        private const val ERROR_VNODE_NOT_IN_MAINTENANCE =
            "Virtual node must be in maintenance to perform an upgrade."

        // Server side messages
        private const val EXPECTED_ERROR_CPB_INSTEAD_OF_CPI = "Invalid CPI.  Unknown Corda-CPI-Format - \"1.0\""

        private val testRunUniqueId = UUID.randomUUID()
        private val aliceX500 = "CN=Alice-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private val bobX500 = "CN=Bob-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private val aliceHoldingId: String = getHoldingIdShortHash(aliceX500, GROUP_ID)
        private val bobHoldingId: String = getHoldingIdShortHash(bobX500, GROUP_ID)
        private val staticMemberList = listOf(
            aliceX500,
            bobX500
        )

        private val cpiName = "${TEST_CPI_NAME}_$testRunUniqueId"
        private val upgradeTestingCpiName = "${VNODE_UPGRADE_TEST_CPI_NAME}_$testRunUniqueId"
        private val otherCpiName = "${TEST_CPI_NAME}_OTHER_$testRunUniqueId"
    }

    /**
     * As long as no-one assigns an order lower than this, this test runs first, and all others, after, which is fine.
     *
     * This *first* test, uploads codesign certificate into the system.
     */
    @Test
    @Order(5)
    fun `can import codesigner certificate`() {
        cluster {
            endpoint(
                CLUSTER_URI,
                USERNAME,
                PASSWORD
            )
            assertWithRetry {
                // Certificate upload can be slow in the combined worker, especially after it has just started up.
                timeout(Duration.ofSeconds(100))
                interval(Duration.ofSeconds(1))
                command { importCertificate(CODE_SIGNER_CERT, "code-signer", "cordadev") }
                condition { it.code == 204 }
            }
        }
    }

    /**
     * This test, uploads a CPI into the system.
     */
    @Test
    @Order(10)
    fun `can upload CPI`() {
        cluster {
            endpoint(
                CLUSTER_URI,
                USERNAME,
                PASSWORD
            )

            val cpiHash = eventuallyUploadCpi(TEST_CPB_LOCATION, cpiName)

            val actualChecksum = getCpiChecksum(cpiName)

            assertThat(actualChecksum).isNotNull.isNotEmpty

            assertThat(cpiHash).withFailMessage(ERROR_CPI_NOT_UPLOADED).isNotNull

            assertThat(actualChecksum).isEqualTo(cpiHash)
        }
    }

    private fun ClusterBuilder.eventuallyUploadCpi(
        cpbLocation: String,
        cpiName: String,
        cpiVersion: String = "1.0.0.0-SNAPSHOT"
    ): String {
        val requestId = cpiUpload(cpbLocation, GROUP_ID, staticMemberList, cpiName, cpiVersion)
            .let { it.toJson()["id"].textValue() }
        assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

        // BUG:  returning "OK" feels 'weakly' typed
        val json = assertWithRetry {
            // CPI upload can be slow in the combined worker, especially after it has just started up.
            timeout(Duration.ofSeconds(100))
            interval(Duration.ofSeconds(2))
            command { cpiStatus(requestId) }
            condition {
                it.code == 200 && it.toJson()["status"].textValue() == "OK"
            }
            immediateFailCondition {
                it.code == CONFLICT.statusCode
                        && null != it.toJson()["details"]
                        && it.toJson()["details"]["code"].textValue().equals(CONFLICT.toString())
                        && null != it.toJson()["title"]
                        && it.toJson()["title"].textValue().contains("already uploaded")
            }
        }.toJson()

        val cpiHash = json["cpiFileChecksum"].textValue()
        assertThat(cpiHash).isNotNull.isNotEmpty

        // Capture the cpiHash from the cpi status upload
        // We probably want more tests like this that enforce "expectations" on the API.
        assertThat(cpiHash!!.length)
            .withFailMessage("Short code length of wrong size - likely this test needs fixing")
            .isEqualTo(12)
        return cpiHash
    }

    /**
     * Runs second to ensure that we reject this with a correct message
     */
    @Test
    @Order(20)
    fun `cannot upload a CPB`() {
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
                            json.has("errorMessage")
                                    && json["errorMessage"].textValue() == EXPECTED_ERROR_CPB_INSTEAD_OF_CPI
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
    @Order(30)
    fun `cannot upload same CPI`() {
        cluster {
            endpoint(
                CLUSTER_URI,
                USERNAME,
                PASSWORD
            )
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
    @Order(31)
    fun `cannot upload same CPI with different groupId`() {
        cluster {
            endpoint(
                CLUSTER_URI,
                USERNAME,
                PASSWORD
            )
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
    @Order(32)
    fun `can upload different CPI with same groupId`() {
        cluster {
            endpoint(
                CLUSTER_URI,
                USERNAME,
                PASSWORD
            )
            val requestId = cpiUpload(
                TEST_CPB_LOCATION,
                "8c5d6948-e17b-44e7-9d1c-fa4a3f667cad",
                staticMemberList,
                otherCpiName
            ).let { it.toJson()["id"].textValue() }

            val json = assertWithRetry {
                // CPI upload can be slow in the combined worker, especially after it has just started up.
                timeout(Duration.ofSeconds(100))
                interval(Duration.ofSeconds(2))
                command { cpiStatus(requestId) }
                condition {
                    it.code == 200 && it.toJson()["status"].textValue() == "OK"
                }
                immediateFailCondition {
                    it.code == CONFLICT.statusCode
                            && null != it.toJson()["details"]
                            && it.toJson()["details"]["code"].textValue().equals(CONFLICT.toString())
                            && null != it.toJson()["title"]
                            && it.toJson()["title"].textValue().contains("already uploaded")
                }
            }.toJson()

            val cpiHash = json["cpiFileChecksum"].textValue()
            assertThat(cpiHash).isNotNull.isNotEmpty
            val actualChecksum = getCpiChecksum(otherCpiName)
            assertThat(actualChecksum).isNotNull.isNotEmpty
            assertThat(cpiHash).withFailMessage(ERROR_CPI_NOT_UPLOADED).isNotNull
            assertThat(actualChecksum).isEqualTo(cpiHash)
        }
    }

    @Test
    @Order(33)
    fun `list cpis`() {
        cluster {
            endpoint(
                CLUSTER_URI,
                USERNAME,
                PASSWORD
            )

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
            endpoint(
                CLUSTER_URI,
                USERNAME,
                PASSWORD
            )

            val json = cpiList().toJson()
            val cpiJson = json["cpis"].first { it["id"]["cpiName"].textValue() == cpiName }

            val groupPolicyJson = cpiJson["groupPolicy"].textValue().toJson()
            assertThat(groupPolicyJson["groupId"].textValue()).isEqualTo(GROUP_ID)
        }
    }

    @Test
    @Order(40)
    fun `can create virtual node with holding id and CPI`() {
        cluster {
            endpoint(
                CLUSTER_URI,
                USERNAME,
                PASSWORD
            )
            val cpiFileChecksum = getCpiChecksum(cpiName)

            eventuallyCreateVirtualNode(cpiFileChecksum, aliceX500)
        }
    }

    private fun ClusterBuilder.eventuallyCreateVirtualNode(cpiFileChecksum: String, x500Name: String): String {
        val vNodeJson = assertWithRetry {
            command { vNodeCreate(cpiFileChecksum, x500Name) }
            condition { it.code == 200 }
            failMessage(ERROR_HOLDING_ID)
        }.toJson()
        val vnodeShortHash = vNodeJson["holdingIdentity"]["shortHash"].textValue()
        assertThat(vnodeShortHash).isNotNull.isNotEmpty
        return vnodeShortHash
    }

    @Test
    @Order(50)
    fun `cannot create duplicate virtual node`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
            val hash = getCpiChecksum(cpiName)

            assertWithRetry {
                command { vNodeCreate(hash, aliceX500) }
                condition { it.code == 409 }
            }
        }
    }

    @Test
    @Order(60)
    fun `list virtual nodes`() {
        cluster {
            endpoint(
                CLUSTER_URI,
                USERNAME,
                PASSWORD
            )

            assertWithRetry {
                timeout(Duration.of(30, ChronoUnit.SECONDS))
                command { vNodeList() }
                condition { response ->
                    val nodes = vNodeList().toJson()["virtualNodes"].map {
                        it["holdingIdentity"]["x500Name"].textValue()
                    }
                    response.code == 200 && nodes.contains(aliceX500)
                }
            }
        }
    }

    @Test
    @Order(61)
    fun `get a virtual node`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            assertWithRetry {
                timeout(Duration.of(30, ChronoUnit.SECONDS))
                command { getVNode(aliceHoldingId) }
                condition { response ->
                    response.code == 200 &&
                        response.toJson()["holdingIdentity"]["x500Name"].textValue().contains(aliceX500)
                }
            }
        }
    }

    @Test
    @Order(62)
    fun `set virtual node state`() {
        cluster {
            endpoint(
                CLUSTER_URI,
                USERNAME,
                PASSWORD
            )
            val vnodesWithStates: List<Pair<String, String>> = vNodeList().toJson()["virtualNodes"].map {
                it["holdingIdentity"]["shortHash"].textValue() to it["flowP2pOperationalStatus"].textValue()
            }

            val (vnodeId, oldState) = vnodesWithStates.last()
            val newState = "maintenance"

            eventuallyUpdateVirtualNodeState(vnodeId, newState, "INACTIVE")

            val className = "net.cordapp.testing.smoketests.virtualnode.ReturnAStringFlow"
            val requestId = startRpcFlow(aliceHoldingId, emptyMap(), className, 503)
            awaitRpcFlowFinished(aliceHoldingId, requestId, 404)

            eventuallyUpdateVirtualNodeState(vnodeId, oldState, "ACTIVE")
        }
    }

    private fun ClusterBuilder.eventuallyUpdateVirtualNodeState(vnodeId: String, newState: String, expectedOperationalStatuses: String) {
        updateVirtualNodeState(vnodeId, newState)

        assertWithRetry {
            timeout(Duration.of(60, ChronoUnit.SECONDS))
            command { vNodeList() }
            condition {
                try {
                    if (it.code == 200) {
                        val vNodeInfo = it.toJson()["virtualNodes"].single { virtualNode ->
                            virtualNode["holdingIdentity"]["shortHash"].textValue() == vnodeId
                        }
                        vNodeInfo["flowP2pOperationalStatus"].textValue() == expectedOperationalStatuses &&
                                vNodeInfo["flowStartOperationalStatus"].textValue() == expectedOperationalStatuses &&
                                vNodeInfo["flowOperationalStatus"].textValue() == expectedOperationalStatuses &&
                                vNodeInfo["vaultDbOperationalStatus"].textValue() == expectedOperationalStatuses
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

    @Test
    @Order(65)
    fun `cpi status returns 400 for unknown request id`() {
        cluster {
            endpoint(
                CLUSTER_URI,
                USERNAME,
                PASSWORD
            )
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
            endpoint(
                CLUSTER_URI,
                USERNAME,
                PASSWORD
            )

            val initialCpiFileChecksum = getCpiFileChecksum(cpiName)

            val requestId = forceCpiUpload(TEST_CPB_LOCATION, GROUP_ID, staticMemberList, cpiName).let { it.toJson()["id"].textValue() }
            assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

            // BUG:  returning "OK" feels 'weakly' typed
            assertWithRetry {
                command { cpiStatus(requestId) }
                condition { it.code == 200 && it.toJson()["status"].textValue() == "OK" }
            }

            // Force uploaded CPI may take some time to propagate through the system and arrive to REST worker's CpiInfoReadService
            eventually(Duration.ofSeconds(100)) {
                assertThat(getCpiFileChecksum(cpiName)).isNotEqualTo(initialCpiFileChecksum)
            }
        }
    }

    @Test
    @Order(81)
    fun `can run the uploaded CPI`() {
        cluster {
            endpoint(
                CLUSTER_URI,
                USERNAME,
                PASSWORD
            )

            runReturnAStringFlow("original-cpi")
        }
    }

    @Test
    @Order(82)
    fun `persist dog`() {
        cluster {
            endpoint(
                CLUSTER_URI,
                USERNAME,
                PASSWORD
            )

            runSimplePersistenceCheckFlow("Could persist dog")
        }
    }

    @Test
    @Order(90)
    fun `can force upload the CPI with a new set of CPKs`() {
        cluster {
            endpoint(
                CLUSTER_URI,
                USERNAME,
                PASSWORD
            )

            val initialCpiFileChecksum = getCpiFileChecksum(cpiName)

            val requestId = forceCpiUpload(CACHE_INVALIDATION_TEST_CPB, GROUP_ID, staticMemberList, cpiName)
                .let { it.toJson()["id"].textValue() }
            assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

            assertWithRetry {
                command { cpiStatus(requestId) }
                condition { it.code == 200 && it.toJson()["status"].textValue() == "OK" }
            }

            eventually(Duration.ofSeconds(120)) {
                assertThat(getCpiFileChecksum(cpiName)).isNotEqualTo(initialCpiFileChecksum)
            }
        }
    }

    @Test
    @Order(91)
    fun `can run the force-uploaded CPI with a change to ReturnAStringFlow`() {
        cluster {
            endpoint(
                CLUSTER_URI,
                USERNAME,
                PASSWORD
            )

            runReturnAStringFlow("force-uploaded-cpi")
        }
    }

    @Test
    @Order(92)
    fun `can sync the virtual node's DB and run a flow on the force uploaded CPI to persist a fish entity`() {
        cluster {
            endpoint(
                CLUSTER_URI,
                USERNAME,
                PASSWORD
            )
            // Status 204 indicates a non-error but no response data
            assertThat(syncVirtualNode(aliceHoldingId).code).isEqualTo(204)

            runSimplePersistenceCheckFlow("Could persist Floaty")
        }
    }

    @Test
    @Order(93)
    fun `can force upload the CPI with CPKs that have no changelogs`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            val initialCpiFileChecksum = getCpiFileChecksum(cpiName)

            val requestId = forceCpiUpload(TEST_CPB_WITHOUT_CHANGELOGS_LOCATION, GROUP_ID, staticMemberList, cpiName)
                .let { it.toJson()["id"].textValue() }
            assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

            assertWithRetry {
                command { cpiStatus(requestId) }
                condition { it.code == 200 && it.toJson()["status"].textValue() == "OK" }
            }

            eventually(Duration.ofSeconds(120)) {
                assertThat(getCpiFileChecksum(cpiName)).isNotEqualTo(initialCpiFileChecksum)
            }
        }
    }

    @Test
    @Order(94)
    fun `can force-sync the virtual node's vault for a CPI with no changelogs`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
            assertThat(syncVirtualNode(aliceHoldingId).code).isEqualTo(204)

            val className = "net.cordapp.testing.smoketests.virtualnode.NoChangelogFlow"
            val requestId = startRpcFlow(aliceHoldingId, emptyMap(), className)
            val flowStatus = awaitRpcFlowFinished(aliceHoldingId, requestId)

            assertThat(flowStatus.flowResult).isEqualTo("NO_CHANGELOG_FLOW_COMPLETE")
        }
    }

    @Test
    @Order(100)
    fun `can force upload the original CPI back again and run a flow that does not interact with the database`() {
        cluster {
            endpoint(
                CLUSTER_URI,
                USERNAME,
                PASSWORD
            )

            val initialCpiFileChecksum = getCpiFileChecksum(cpiName)

            val requestId = forceCpiUpload(TEST_CPB_LOCATION, GROUP_ID, staticMemberList, cpiName)
                .let { it.toJson()["id"].textValue() }
            assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

            assertWithRetry {
                command { cpiStatus(requestId) }
                condition { it.code == 200 && it.toJson()["status"].textValue() == "OK" }
            }

            eventually(Duration.ofSeconds(100)) {
                assertThat(getCpiFileChecksum(cpiName)).isNotEqualTo(initialCpiFileChecksum)
            }

            runReturnAStringFlow("original-cpi")
        }
    }

    @Test
    @Order(101)
    fun `can sync the vault DB again and run a flow from the original CPI that persists a dog entity`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
            assertThat(syncVirtualNode(aliceHoldingId).code).isEqualTo(204)

            runSimplePersistenceCheckFlow("Could persist dog")
        }
    }

    @Test
    @Order(110)
    fun `can upload v1 and v2 of a CPI with the same name`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            eventuallyUploadCpi(VNODE_UPGRADE_TEST_CPI_V1, upgradeTestingCpiName, "v1")
            eventuallyUploadCpi(VNODE_UPGRADE_TEST_CPI_V2, upgradeTestingCpiName, "v2")
        }
    }

    @Test
    @Order(111)
    fun `prepare a virtual node with v1 CPI`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            val cpiV1 = getCpiChecksum(upgradeTestingCpiName, "v1")
            eventuallyCreateVirtualNode(cpiV1, bobX500)
            eventuallyAssertVirtualNodeHasCpi(bobHoldingId, upgradeTestingCpiName, "v1")

            runReturnAStringFlow("upgrade-test-v1", bobHoldingId)
            runSimplePersistenceCheckFlow("Could persist fish", bobHoldingId)
        }
    }

    @Test
    @Order(112)
    fun `upgrading without transitioning virtual node to maintenance fails with bad request`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            val cpiV2 = getCpiChecksum(upgradeTestingCpiName, "v2")

            assertWithRetry {
                command { vNodeUpgrade(bobHoldingId, cpiV2) }
                condition { it.code == 400 }
                failMessage(ERROR_VNODE_NOT_IN_MAINTENANCE)
            }.toJson()

        }
    }

    @Test
    @Order(113)
    fun `can upgrade a virtual node's CPI when it is in maintenance`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            eventuallyUpdateVirtualNodeState(bobHoldingId, "maintenance", "INACTIVE")

            val cpiV2 = getCpiChecksum(upgradeTestingCpiName, "v2")
            triggerVirtualNodeUpgrade(bobHoldingId, cpiV2)
            eventuallyAssertVirtualNodeHasCpi(bobHoldingId, upgradeTestingCpiName, "v2")

            eventuallyUpdateVirtualNodeState(bobHoldingId, "active", "ACTIVE")

            runReturnAStringFlow("upgrade-test-v2", bobHoldingId)
            runSimplePersistenceCheckFlow("Could persist dog", bobHoldingId)
        }
    }

    private fun ClusterBuilder.triggerVirtualNodeUpgrade(
        virtualNodeShortHash: String, targetCpiFileChecksum: String
    ): String? {
        val vNodeJson = assertWithRetry {
            command { vNodeUpgrade(virtualNodeShortHash, targetCpiFileChecksum) }
            condition { it.code == 202 }
            failMessage(ERROR_HOLDING_ID)
        }.toJson()
        return vNodeJson["requestId"].textValue()
    }

    private fun ClusterBuilder.eventuallyAssertVirtualNodeHasCpi(
        virtualNodeShortHash: String, cpiName: String, cpiVersion: String
    ) = assertWithRetry {
        timeout(Duration.of(30, ChronoUnit.SECONDS))
        command { getVNode(virtualNodeShortHash) }
        condition { response ->
            response.code == 200 &&
                    response.toJson()["holdingIdentity"]["x500Name"].textValue().contains(bobX500) &&
                    response.toJson()["cpiIdentifier"]["cpiName"].textValue().equals(cpiName) &&
                    response.toJson()["cpiIdentifier"]["cpiVersion"].textValue().equals(cpiVersion)
        }
    }

    private fun runReturnAStringFlow(expectedResult: String, holdingId: String = aliceHoldingId) {
        val className = "net.cordapp.testing.smoketests.virtualnode.ReturnAStringFlow"

        val requestId = startRpcFlow(holdingId, emptyMap(), className)

        val flowStatus = awaitRpcFlowFinished(holdingId, requestId)

        assertThat(flowStatus.flowResult).isEqualTo(expectedResult)
    }

    private fun runSimplePersistenceCheckFlow(expectedResult: String, holdingId: String = aliceHoldingId) {
        val className = "net.cordapp.testing.smoketests.virtualnode.SimplePersistenceCheckFlow"

        val requestId = startRpcFlow(holdingId, emptyMap(), className)

        val flowStatus = awaitRpcFlowFinished(holdingId, requestId)

        assertThat(flowStatus.flowResult).isEqualTo(expectedResult)
    }

    private fun ClusterBuilder.getCpiFileChecksum(cpiName: String): String {
        val cpis = cpiList().toJson()["cpis"]
        val cpiJson = cpis.toList().first { it["id"]["cpiName"].textValue() == cpiName }
        return cpiJson["cpiFileFullChecksum"].toString()
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

    private fun ClusterBuilder.getCpiChecksum(cpiName: String, cpiVersion: String): String {
        val cpiFileChecksum = eventually {
            val cpis = cpiList().toJson()["cpis"]
            val cpiJson = cpis.toList().find {
                it["id"]["cpiName"].textValue() == cpiName && it["id"]["cpiVersion"].textValue() == cpiVersion
            }
            assertNotNull(cpiJson, "Cpi with name $cpiName not yet found in cpi list.")
            truncateLongHash(cpiJson!!["cpiFileChecksum"].textValue())
        }
        return cpiFileChecksum
    }
}
