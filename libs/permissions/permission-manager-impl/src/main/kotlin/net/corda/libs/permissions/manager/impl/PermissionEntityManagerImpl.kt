package net.corda.libs.permissions.manager.impl

import java.time.Duration
import net.corda.data.permissions.Permission
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.permission.CreatePermissionRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.manager.PermissionEntityManager
import net.corda.libs.permissions.manager.common.PermissionTypeEnum
import net.corda.libs.permissions.manager.exception.PermissionManagerException
import net.corda.data.permissions.management.permission.PermissionTypeEnum as AvroPermissionTypeEnum
import net.corda.libs.permissions.manager.impl.converter.convertToResponseDto
import net.corda.libs.permissions.manager.request.CreatePermissionRequestDto
import net.corda.libs.permissions.manager.request.GetPermissionRequestDto
import net.corda.libs.permissions.manager.response.PermissionResponseDto
import net.corda.messaging.api.publisher.RPCSender
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.Try

class PermissionEntityManagerImpl(
    config: SmartConfig,
    private val rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>,
    private val permissionCache: PermissionCache
) : PermissionEntityManager {

    private companion object {
        const val ENDPOINT_TIMEOUT_PATH = "endpointTimeoutMs"
        const val DEFAULT_ENDPOINT_TIMEOUT_MS = 10000L
    }

    private val writerTimeout = initializeEndpointTimeoutDuration(config)

    private fun initializeEndpointTimeoutDuration(config: SmartConfig): Duration {
        return if (config.hasPath(ENDPOINT_TIMEOUT_PATH)) {
            Duration.ofMillis(config.getLong(ENDPOINT_TIMEOUT_PATH))
        } else {
            Duration.ofMillis(DEFAULT_ENDPOINT_TIMEOUT_MS)
        }
    }

    override fun createPermission(createPermissionRequestDto: CreatePermissionRequestDto): Try<PermissionResponseDto> {
        return Try.on {
            val future = rpcSender.sendRequest(
                PermissionManagementRequest(
                    createPermissionRequestDto.requestedBy,
                    "cluster",
                    CreatePermissionRequest(
                        createPermissionRequestDto.permissionType.map(),
                        createPermissionRequestDto.permissionString,
                        createPermissionRequestDto.groupVisibility
                    )
                )
            )

            val futureResponse = future.getOrThrow(writerTimeout)

            val result = futureResponse.response
            if (result !is Permission)
                throw PermissionManagerException("Unknown response for Create Permission operation: $result")

            result.convertToResponseDto()
        }
    }

    override fun getPermission(permissionRequestDto: GetPermissionRequestDto): PermissionResponseDto? {
        val cachedPermission: Permission = permissionCache.getPermission(permissionRequestDto.permissionId) ?: return null
        return cachedPermission.convertToResponseDto()
    }
}

private fun PermissionTypeEnum.map(): AvroPermissionTypeEnum {
    return when(this) {
        PermissionTypeEnum.ALLOW -> AvroPermissionTypeEnum.ALLOW
        PermissionTypeEnum.DENY -> AvroPermissionTypeEnum.DENY
    }
}
