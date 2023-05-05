package net.corda.applications.workers.smoketest.virtualnode

import com.fasterxml.jackson.databind.JsonNode
import net.corda.applications.workers.smoketest.TEST_CPB_LOCATION
import net.corda.applications.workers.smoketest.TEST_CPI_NAME
import net.corda.applications.workers.smoketest.VNODE_UPGRADE_TEST_CPI_NAME
import net.corda.applications.workers.smoketest.VNODE_UPGRADE_TEST_CPI_V1
import net.corda.applications.workers.smoketest.VNODE_UPGRADE_TEST_CPI_V2
import net.corda.e2etest.utilities.CLUSTER_URI
import net.corda.e2etest.utilities.CODE_SIGNER_CERT
import net.corda.e2etest.utilities.ClusterBuilder
import net.corda.e2etest.utilities.PASSWORD
import net.corda.e2etest.utilities.USERNAME
import net.corda.e2etest.utilities.assertWithRetry
import net.corda.e2etest.utilities.awaitRpcFlowFinished
import net.corda.e2etest.utilities.awaitVirtualNodeOperationStatusCheck
import net.corda.e2etest.utilities.cluster
import net.corda.e2etest.utilities.getHoldingIdShortHash
import net.corda.e2etest.utilities.startRpcFlow
import net.corda.e2etest.utilities.toJson
import net.corda.e2etest.utilities.truncateLongHash
import net.corda.rest.ResponseCode.CONFLICT
import net.corda.rest.asynchronous.v1.AsyncOperationState
import net.corda.test.util.eventually
import net.corda.utilities.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.util.UUID

/**
 * Any 'unordered' tests are run *last*
 */
@Order(10)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class VirtualNodeRestTest {
    companion object {
        // Some simple test failure messages
        private const val ERROR_CPI_NOT_UPLOADED =
            "CPI has not been uploaded during this run - this test needs to be run on a clean cluster."
        private const val ERROR_IS_CLUSTER_RUNNING = "Initial upload failed - is the cluster running?"
        private const val ERROR_HOLDING_ID =
            "Holding id could not be created - this test needs to be run on a clean cluster."
        private const val ERROR_REQUEST_ID = "Request Id not found."

        private val testRunUniqueId = UUID.randomUUID()
        private val groupId = UUID.randomUUID().toString()
        private val aliceX500 = "CN=Alice-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private val bobX500 = "CN=Bob-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private val aliceHoldingId: String = getHoldingIdShortHash(aliceX500, groupId)
        private val bobHoldingId: String = getHoldingIdShortHash(bobX500, groupId)
        private val staticMemberList = listOf(
            aliceX500,
            bobX500
        )

        private val cpiName = "${TEST_CPI_NAME}_$testRunUniqueId"
        private val upgradeTestingCpiName = "${VNODE_UPGRADE_TEST_CPI_NAME}_$testRunUniqueId"
        private val otherCpiName = "${TEST_CPI_NAME}_OTHER_$testRunUniqueId"

        private val retryTimeout = 120.seconds
        private val retryInterval = 1.seconds
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
                timeout(retryTimeout)
                interval(retryInterval)
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
        val requestId = cpiUpload(cpbLocation, groupId, staticMemberList, cpiName, cpiVersion)
            .let { it.toJson()["id"].textValue() }
        assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

        // BUG:  returning "OK" feels 'weakly' typed
        val json = assertWithRetry {
            // CPI upload can be slow in the combined worker, especially after it has just started up.
            timeout(retryTimeout)
            interval(retryInterval)
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
                timeout(retryTimeout)
                interval(retryInterval)
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
                timeout(retryTimeout)
                interval(retryInterval)
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
            assertThat(groupPolicyJson["groupId"].textValue()).isEqualTo(groupId)
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
            timeout(retryTimeout)
            interval(retryInterval)
            command { vNodeCreate(cpiFileChecksum, x500Name) }
            condition { it.code == 202 }
            failMessage(ERROR_HOLDING_ID)
        }.toJson()
        val requestId = vNodeJson["requestId"].textValue()
        assertThat(requestId).isNotNull.isNotEmpty

        return awaitVirtualNodeOperationStatusCheck(requestId)
    }

    @Test
    @Order(50)
    fun `cannot create duplicate virtual node`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
            val hash = getCpiChecksum(cpiName)

            assertWithRetry {
                timeout(retryTimeout)
                interval(retryInterval)
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
                timeout(retryTimeout)
                interval(retryInterval)
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
                timeout(retryTimeout)
                interval(retryInterval)
                command { getVNode(aliceHoldingId) }
                condition { response ->
                    response.code == 200 &&
                            response.toJson()["holdingIdentity"]["x500Name"].textValue().contains(aliceX500)
                }
            }
        }
    }

    private fun ClusterBuilder.eventuallyUpdateVirtualNodeState(
        vnodeId: String,
        newState: String,
        expectedOperationalStatuses: String
    ) {
        updateVirtualNodeState(vnodeId, newState)

        assertWithRetry {
            timeout(retryTimeout)
            interval(retryInterval)
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
                    println("Failed, response: $it")
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
                timeout(retryTimeout)
                interval(retryInterval)
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

            val requestId = forceCpiUpload(
                TEST_CPB_LOCATION,
                groupId,
                staticMemberList,
                cpiName
            ).let { it.toJson()["id"].textValue() }
            assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

            assertWithRetry {
                timeout(retryTimeout)
                interval(retryInterval)
                command { cpiStatus(requestId) }
                condition { it.code == 200 && it.toJson()["status"].textValue() == "OK" }
            }

            // Force uploaded CPI may take some time to propagate through the system and arrive to REST worker's CpiInfoReadService
            eventually(retryTimeout, retryInterval) {
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
    @Order(110)
    fun `can upload multiple versions of a CPI with the same name`() {
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
            awaitVirtualNodeOperationCompletion(bobHoldingId)

            runReturnAStringFlow("upgrade-test-v1", bobHoldingId)
            runSimplePersistenceCheckFlow("Could persist fish", bobHoldingId)
        }
    }

    @Test
    @Order(113)
    fun `can upgrade a virtual node's CPI when it is in maintenance`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            eventuallyUpdateVirtualNodeState(bobHoldingId, "maintenance", "INACTIVE")

            val cpiV2 = getCpiChecksum(upgradeTestingCpiName, "v2")
            val requestId = triggerVirtualNodeUpgrade(bobHoldingId, cpiV2)

            val statusAfterUpgrade = getVirtualNodeOperationStatus(requestId)
            val operationState = statusAfterUpgrade["status"].textValue()
            assertThat(operationState).isIn(AsyncOperationState.SUCCEEDED.name, AsyncOperationState.IN_PROGRESS.name)

            eventuallyAssertVirtualNodeHasCpi(bobHoldingId, upgradeTestingCpiName, "v2")
            awaitVirtualNodeOperationCompletion(bobHoldingId)

            val statusAfterCompletion = getVirtualNodeOperationStatus(requestId)
            val operationStateAfterCompletion = statusAfterCompletion["status"].textValue()
            assertThat(operationStateAfterCompletion).isEqualTo(AsyncOperationState.SUCCEEDED.name)
        }
    }

    @Test
    @Order(114)
    fun `can change virtual node's state to active and run a flow after upgrade`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            eventuallyUpdateVirtualNodeState(bobHoldingId, "active", "ACTIVE")

            runReturnAStringFlow("upgrade-test-v2", bobHoldingId)
            runSimplePersistenceCheckFlow("Could persist dog", bobHoldingId)
        }
    }

    private fun ClusterBuilder.triggerVirtualNodeUpgrade(
        virtualNodeShortHash: String, targetCpiFileChecksum: String
    ): String {
        val vNodeJson = assertWithRetry {
            timeout(retryTimeout)
            interval(retryInterval)
            command { vNodeUpgrade(virtualNodeShortHash, targetCpiFileChecksum) }
            condition { it.code == 202 }
            failMessage(ERROR_HOLDING_ID)
        }.toJson()
        return vNodeJson["requestId"].textValue()
    }

    private fun ClusterBuilder.getVirtualNodeOperationStatus(requestId: String): JsonNode {
        val operationStatus = assertWithRetry {
            timeout(retryTimeout)
            interval(retryInterval)
            command { getVNodeOperationStatus(requestId) }
            condition { it.code == 200 }
            failMessage(ERROR_REQUEST_ID)
        }.toJson()
        return operationStatus
    }

    private fun ClusterBuilder.eventuallyAssertVirtualNodeHasCpi(
        virtualNodeShortHash: String, cpiName: String, cpiVersion: String
    ) = assertWithRetry {
        timeout(retryTimeout)
        interval(retryInterval)
        command { getVNode(virtualNodeShortHash) }
        condition { response ->
            response.code == 200 &&
                    response.toJson()["cpiIdentifier"]["cpiName"].textValue().equals(cpiName) &&
                    response.toJson()["cpiIdentifier"]["cpiVersion"].textValue().equals(cpiVersion)
        }
    }

    private fun ClusterBuilder.awaitVirtualNodeOperationCompletion(virtualNodeShortHash: String) = assertWithRetry {
        timeout(retryTimeout)
        interval(retryInterval)
        command { getVNode(virtualNodeShortHash) }
        condition { response ->
            response.code == 200 && response.toJson()["operationInProgress"].isNull
        }
    }

    private fun runReturnAStringFlow(expectedResult: String, holdingId: String = aliceHoldingId) {
        val className = "com.r3.corda.testing.smoketests.virtualnode.ReturnAStringFlow"

        val requestId = startRpcFlow(holdingId, emptyMap(), className)

        val flowStatus = awaitRpcFlowFinished(holdingId, requestId)

        assertThat(flowStatus.flowResult!!.textValue()).isEqualTo(expectedResult)
    }

    private fun runSimplePersistenceCheckFlow(expectedResult: String, holdingId: String = aliceHoldingId) {
        val className = "com.r3.corda.testing.smoketests.virtualnode.SimplePersistenceCheckFlow"

        val requestId = startRpcFlow(holdingId, emptyMap(), className)

        val flowStatus = awaitRpcFlowFinished(holdingId, requestId)

        assertThat(flowStatus.flowResult!!.textValue()).isEqualTo(expectedResult)
    }

    private fun ClusterBuilder.getCpiFileChecksum(cpiName: String): String {
        val cpis = cpiList().toJson()["cpis"]
        val cpiJson = cpis.toList().first { it["id"]["cpiName"].textValue() == cpiName }
        return cpiJson["cpiFileFullChecksum"].toString()
    }

    private fun ClusterBuilder.getCpiChecksum(cpiName: String): String {
        val cpiFileChecksum = eventually(retryTimeout, retryInterval) {
            val cpis = cpiList().toJson()["cpis"]
            val cpiJson = cpis.toList().find { it["id"]["cpiName"].textValue() == cpiName }
            assertNotNull(cpiJson, "Cpi with name $cpiName not yet found in cpi list.")
            truncateLongHash(cpiJson!!["cpiFileChecksum"].textValue())
        }
        return cpiFileChecksum
    }

    private fun ClusterBuilder.getCpiChecksum(cpiName: String, cpiVersion: String): String {
        val cpiFileChecksum = eventually(retryTimeout, retryInterval) {
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
