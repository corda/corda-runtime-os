package net.corda.applications.workers.smoketest

import net.corda.e2etest.utilities.ClusterBuilder
import net.corda.e2etest.utilities.assertWithRetry
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
    groupId: String = defaultGroupId.toString(),
    staticMemberList: List<String> = defaultStaticMemberList
): String {
    val requestId = cpiUpload(cpbLocation,
        groupId,
        staticMemberList, cpiName, cpiVersion)
        .let { it.toJson()["id"].textValue() }
    Assertions.assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

    // BUG:  returning "OK" feels 'weakly' typed
    val json = assertWithRetry {
        timeout(retryTimeout)
        interval(retryInterval)
        command { cpiStatus(requestId) }
        condition {
            it.code == 200 && it.toJson()["status"].textValue() == "OK"
        }
        immediateFailCondition {
            it.code == ResponseCode.CONFLICT.statusCode
                    && null != it.toJson()["details"]
                    && it.toJson()["details"]["code"].textValue().equals(ResponseCode.CONFLICT.toString())
                    && null != it.toJson()["title"]
                    && it.toJson()["title"].textValue().contains("already uploaded")
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

