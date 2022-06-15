package net.corda.libs.permissions.manager.impl

import net.corda.data.permissions.Permission
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.permission.CreatePermissionRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.management.cache.PermissionManagementCache
import net.corda.libs.permissions.manager.PermissionEntityManager
import net.corda.libs.permissions.manager.impl.SmartConfigUtil.getEndpointTimeout
import net.corda.libs.permissions.manager.impl.converter.convertToResponseDto
import net.corda.libs.permissions.manager.impl.converter.toAvroType
import net.corda.libs.permissions.manager.request.CreatePermissionRequestDto
import net.corda.libs.permissions.manager.request.GetPermissionRequestDto
import net.corda.libs.permissions.manager.response.PermissionResponseDto
import net.corda.messaging.api.publisher.RPCSender
import java.util.concurrent.atomic.AtomicReference

class PermissionEntityManagerImpl(
    config: SmartConfig,
    private val rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>,
    private val permissionManagementCacheRef: AtomicReference<PermissionManagementCache?>,
) : PermissionEntityManager {

    private val writerTimeout = config.getEndpointTimeout()

    override fun createPermission(createPermissionRequestDto: CreatePermissionRequestDto): PermissionResponseDto {
        val result = sendPermissionWriteRequest<Permission>(
            rpcSender,
            writerTimeout,
            PermissionManagementRequest(
                createPermissionRequestDto.requestedBy,
                createPermissionRequestDto.virtualNode,
                CreatePermissionRequest(
                    createPermissionRequestDto.permissionType.toAvroType(),
                    createPermissionRequestDto.permissionString,
                    createPermissionRequestDto.groupVisibility
                )
            )
        )

        return result.convertToResponseDto()
    }

    override fun getPermission(permissionRequestDto: GetPermissionRequestDto): PermissionResponseDto? {
        val permissionManagementCache = checkNotNull(permissionManagementCacheRef.get()) {
            "Permission management cache is null."
        }

        val cachedPermission: Permission =
            permissionManagementCache.getPermission(permissionRequestDto.permissionId) ?: return null
        return cachedPermission.convertToResponseDto()
    }
}