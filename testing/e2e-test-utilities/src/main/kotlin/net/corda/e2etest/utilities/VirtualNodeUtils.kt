package net.corda.e2etest.utilities

import net.corda.utilities.minutes
import org.assertj.core.api.Assertions.assertThat

fun ClusterBuilder.awaitVirtualNodeOperationStatusCheck(requestId: String): String? {
    val statusResponse = assertWithRetry {
        timeout(1.minutes)
        command { getVNodeStatus(requestId) }
        condition {
            if (it.code != 200) {
                false
            } else {
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

    return  statusResponse.toJson()["resourceId"]?.textValue()
}