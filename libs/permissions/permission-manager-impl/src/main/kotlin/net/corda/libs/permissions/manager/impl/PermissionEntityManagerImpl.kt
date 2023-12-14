package net.corda.libs.permissions.manager.impl

import net.corda.data.permissions.Permission
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.permission.BulkCreatePermissionsRequest
import net.corda.data.permissions.management.permission.BulkCreatePermissionsResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.management.cache.PermissionManagementCache
import net.corda.libs.permissions.manager.PermissionEntityManager
import net.corda.libs.permissions.manager.impl.SmartConfigUtil.getEndpointTimeout
import net.corda.libs.permissions.manager.impl.converter.convertToAvro
import net.corda.libs.permissions.manager.impl.converter.convertToResponseDto
import net.corda.libs.permissions.manager.impl.converter.toAvroType
import net.corda.libs.permissions.manager.request.CreatePermissionRequestDto
import net.corda.libs.permissions.manager.request.CreatePermissionsRequestDto
import net.corda.libs.permissions.manager.request.GetPermissionRequestDto
import net.corda.libs.permissions.manager.request.QueryPermissionsRequestDto
import net.corda.libs.permissions.manager.response.PermissionResponseDto
import net.corda.libs.permissions.manager.response.PermissionsResponseDto
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
                createPermissionRequestDto.convertToAvro()
            )
        )

        return result.convertToResponseDto()
    }

    override fun createPermissions(createPermissionsRequestDto: CreatePermissionsRequestDto): PermissionsResponseDto {
        val result = sendPermissionWriteRequest<BulkCreatePermissionsResponse>(
            rpcSender,
            writerTimeout,
            PermissionManagementRequest(
                createPermissionsRequestDto.permissionToCreate.first().requestedBy,
                null,
                BulkCreatePermissionsRequest(
                    createPermissionsRequestDto.permissionToCreate.map { it.convertToAvro() },
                    createPermissionsRequestDto.roleIds.toList()
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

    override fun queryPermissions(permissionsQuery: QueryPermissionsRequestDto): List<PermissionResponseDto> {
        val permissionManagementCache = checkNotNull(permissionManagementCacheRef.get()) {
            "Permission management cache is null."
        }

        return permissionManagementCache.permissions.values.filter {
            permissionsQuery.permissionType.toAvroType() == it.permissionType &&
                permissionsQuery.groupVisibility == it.groupVisibility &&
                permissionsQuery.virtualNode == it.virtualNode &&
                permissionsQuery.permissionStringPrefix?.let { psp -> it.permissionString.startsWith(psp) } ?: true
        }.take(permissionsQuery.limit).map { it.convertToResponseDto() }.toList()
    }
}
