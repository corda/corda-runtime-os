package net.corda.e2etest.utilities

import net.corda.rest.ResponseCode
import net.corda.utilities.minutes
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import java.time.Duration

/**
 * Given previously submitted [requestId], this method blocks till request is completed.
 * If operation has been completed successfully, the method returns the short hash id of the vNode.
 */
fun ClusterBuilder.awaitVirtualNodeOperationStatusCheck(
    requestId: String,
    timeout: Duration = 3.minutes
): String {
    val statusResponse = assertWithRetryIgnoringExceptions {
        timeout(timeout)
        command { getVNodeStatus(requestId) }
        condition {
            it.code == ResponseCode.OK.statusCode && run {
                val json = it.toJson()
                val status = json["status"].textValue()
                !(status == "ACCEPTED" || status == "IN_PROGRESS")
            }
        }
        failMessage(
            "The virtual node operation status check failed for '$requestId'"
        )
    }

    val status = statusResponse.toJson()["status"].textValue()

    assertThat(status)
        .withFailMessage("Virtual node operation failed '${statusResponse.body}'")
        .isEqualTo("SUCCEEDED")

    return statusResponse.toJson()["resourceId"]?.textValue()
        ?: fail("Virtual node status response did not include a resourceId field")
}