package net.corda.libs.permissions.manager.impl

import java.time.Duration
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.libs.permissions.manager.exception.UnexpectedPermissionResponseException
import net.corda.libs.permissions.manager.exception.RemotePermissionManagementException
import net.corda.messaging.api.publisher.RPCSender
import net.corda.v5.base.concurrent.getOrThrow

@Suppress("ThrowsCount")
inline fun <reified T : Any> sendPermissionWriteRequest(
    rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>,
    timeout: Duration,
    permissionManagementRequest: PermissionManagementRequest,
): T {

    val future = rpcSender.sendRequest(permissionManagementRequest)

    val futureResponse = future.getOrThrow(timeout)

    if (!futureResponse.success) {
        throw RemotePermissionManagementException(
            futureResponse.exception.errorType,
            futureResponse.exception.errorMessage
        )
    }

    val result = futureResponse.response
    if (result !is T) {
        throw UnexpectedPermissionResponseException("Unknown response type for permission management request: ${result::class.java.name}")
    }

    return result
}
