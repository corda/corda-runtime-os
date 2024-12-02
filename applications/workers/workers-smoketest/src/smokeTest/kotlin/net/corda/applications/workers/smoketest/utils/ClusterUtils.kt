package net.corda.applications.workers.smoketest.utils

import net.corda.e2etest.utilities.ClusterBuilder
import net.corda.e2etest.utilities.assertWithRetry
import net.corda.e2etest.utilities.assertWithRetryIgnoringExceptions
import net.corda.e2etest.utilities.awaitVirtualNodeOperationStatusCheck
import net.corda.rest.ResponseCode
import org.assertj.core.api.Assertions

/**
 * Upload a CPI to the cluster, utilizing a retry mechanism to handle potential delays or conflicts
 * that may occur during the upload.
 *
 * @return The unique hash associated with the CPI
 */
fun ClusterBuilder.eventuallyUploadCpi(
    cpbLocation: String,
    cpiName: String,
    cpiVersion: String = "1.0.0.0-SNAPSHOT",
    groupId: String,
    staticMemberList: List<String>
): String {
    val requestId = cpiUpload(cpbLocation,
        groupId,
        staticMemberList, cpiName, cpiVersion)
        .let { it.toJson()["id"].textValue() }
    Assertions.assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

    val json = assertWithRetryIgnoringExceptions {
        timeout(retryTimeout)
        interval(retryInterval)
        command { cpiStatus(requestId) }
        condition {
            it.code == ResponseCode.OK.statusCode && it.toJson()["status"].textValue() == "OK"
        }
        immediateFailCondition {
            it.code == ResponseCode.CONFLICT.statusCode
        }
    }.toJson()

    val cpiHash = json["cpiFileChecksum"].textValue()
    Assertions.assertThat(cpiHash).isNotNull.isNotEmpty

    // Capture the cpiHash from the cpi status upload
    // We probably want more tests like this that enforce "expectations" on the API.
    Assertions.assertThat(cpiHash!!.length)
        .withFailMessage("Short code length of wrong size - likely this test needs fixing")
        .isEqualTo(12)
    return cpiHash
}

fun ClusterBuilder.eventuallyCreateVirtualNode(cpiFileChecksum: String, x500Name: String): String {
    val vNodeJson = assertWithRetry {
        timeout(retryTimeout)
        interval(retryInterval)
        command { vNodeCreate(cpiFileChecksum, x500Name) }
        condition { it.code == 202 }
        failMessage(ERROR_HOLDING_ID)
    }.toJson()
    val requestId = vNodeJson["requestId"].textValue()
    Assertions.assertThat(requestId).isNotNull.isNotEmpty

    return awaitVirtualNodeOperationStatusCheck(requestId)
}
