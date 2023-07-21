package net.corda.applications.workers.smoketest.virtualnode

import net.corda.applications.workers.smoketest.*
import net.corda.e2etest.utilities.ClusterBuilder
import net.corda.e2etest.utilities.assertWithRetry
import net.corda.e2etest.utilities.awaitVirtualNodeOperationStatusCheck
import net.corda.e2etest.utilities.cluster
import net.corda.e2etest.utilities.getHoldingIdShortHash
import net.corda.e2etest.utilities.truncateLongHash
import net.corda.rest.ResponseCode.CONFLICT
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

        private val testRunUniqueId = UUID.randomUUID()
        private val groupId = UUID.randomUUID().toString()
        private val aliceX500 = "CN=Alice-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private val bobX500 = "CN=Bob-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private val aliceHoldingId: String = getHoldingIdShortHash(aliceX500, groupId)
        private val staticMemberList = listOf(
            aliceX500,
            bobX500
        )

        private val cpiName = "${TEST_CPI_NAME}_$testRunUniqueId"
        private val upgradeTestingCpiName = "${VNODE_UPGRADE_TEST_CPI_NAME}_$testRunUniqueId"

        private val retryTimeout = 120.seconds
        private val retryInterval = 1.seconds

        private val vNodeInitializer = VirtualNodeInitializer
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
    @Order(33)
    fun `list cpis`() {
        cluster {
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
    @Order(40)
    fun `can create virtual node with holding id and CPI`() {
        cluster {
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
    @Order(60)
    fun `list virtual nodes`() {
        cluster {
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

    @Test
    @Order(110)
    fun `can upload multiple versions of a CPI with the same name`() {
        cluster {
            eventuallyUploadCpi(VNODE_UPGRADE_TEST_CPI_V1, upgradeTestingCpiName, "v1")
            eventuallyUploadCpi(VNODE_UPGRADE_TEST_CPI_V2, upgradeTestingCpiName, "v2")
        }
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
}
