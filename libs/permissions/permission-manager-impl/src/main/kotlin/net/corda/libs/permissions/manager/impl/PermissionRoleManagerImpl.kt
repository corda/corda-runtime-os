package net.corda.libs.permissions.manager.impl

import net.corda.data.permissions.Role
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.role.AddPermissionToRoleRequest
import net.corda.data.permissions.management.role.CreateRoleRequest
import net.corda.data.permissions.management.role.RemovePermissionFromRoleRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.cache.PermissionManagementCache
import net.corda.libs.permissions.manager.PermissionRoleManager
import net.corda.libs.permissions.manager.impl.SmartConfigUtil.getEndpointTimeout
import net.corda.libs.permissions.manager.impl.converter.convertToResponseDto
import net.corda.libs.permissions.manager.request.CreateRoleRequestDto
import net.corda.libs.permissions.manager.request.GetRoleRequestDto
import net.corda.libs.permissions.manager.response.RoleResponseDto
import net.corda.messaging.api.publisher.RPCSender


class PermissionRoleManagerImpl(
    config: SmartConfig,
    private val rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>,
    private val permissionManagementCache: PermissionManagementCache,
) : PermissionRoleManager {

    private val writerTimeout = config.getEndpointTimeout()

    override fun createRole(createRoleRequestDto: CreateRoleRequestDto): RoleResponseDto {
        val result = sendPermissionWriteRequest<Role>(
            rpcSender,
            writerTimeout,
            PermissionManagementRequest(
                createRoleRequestDto.requestedBy,
                null,
                CreateRoleRequest(
                    createRoleRequestDto.roleName,
                    createRoleRequestDto.groupVisibility
                )
            )
        )

        return result.convertToResponseDto()
    }

    override fun getRole(roleRequestDto: GetRoleRequestDto): RoleResponseDto? {
        val cachedRole: Role = permissionManagementCache.getRole(roleRequestDto.roleId) ?: return null
        return cachedRole.convertToResponseDto()
    }

    override fun addPermissionToRole(roleId: String, permissionId: String, principal: String): RoleResponseDto {
        val result = sendPermissionWriteRequest<Role>(
            rpcSender,
            writerTimeout,
            PermissionManagementRequest(
                principal,
                null,
                AddPermissionToRoleRequest(
                    roleId,
                    permissionId
                )
            )
        )

        return result.convertToResponseDto()
    }

    override fun removePermissionFromRole(roleId: String, permissionId: String, principal: String): RoleResponseDto {
        val result = sendPermissionWriteRequest<Role>(
            rpcSender,
            writerTimeout,
            PermissionManagementRequest(
                principal,
                null,
                RemovePermissionFromRoleRequest(
                    roleId,
                    permissionId
                )
            )
        )

        return result.convertToResponseDto()
    }
}