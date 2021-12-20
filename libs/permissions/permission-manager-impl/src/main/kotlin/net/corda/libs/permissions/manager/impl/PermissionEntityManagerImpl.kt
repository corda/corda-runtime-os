package net.corda.libs.permissions.manager.impl

import net.corda.data.permissions.Permission
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.permission.CreatePermissionRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.manager.PermissionEntityManager
import net.corda.libs.permissions.manager.exception.PermissionManagerException
import net.corda.libs.permissions.manager.impl.SmartConfigUtil.getEndpointTimeout
import net.corda.libs.permissions.manager.impl.converter.convertToResponseDto
import net.corda.libs.permissions.manager.impl.converter.fromInternal
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

    private val writerTimeout = config.getEndpointTimeout()

    override fun createPermission(createPermissionRequestDto: CreatePermissionRequestDto): Try<PermissionResponseDto> {
        return Try.on {
            val future = rpcSender.sendRequest(
                PermissionManagementRequest(
                    createPermissionRequestDto.requestedBy,
                    "cluster",
                    CreatePermissionRequest(
                        createPermissionRequestDto.permissionType.fromInternal(),
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