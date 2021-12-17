package net.corda.libs.permissions.manager.impl

import net.corda.data.permissions.Role
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.role.CreateRoleRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.manager.PermissionRoleManager
import net.corda.libs.permissions.manager.exception.PermissionManagerException
import net.corda.libs.permissions.manager.impl.SmartConfigUtil.getEndpointTimeout
import net.corda.libs.permissions.manager.impl.converter.convertToResponseDto
import net.corda.libs.permissions.manager.request.CreateRoleRequestDto
import net.corda.libs.permissions.manager.request.GetRoleRequestDto
import net.corda.libs.permissions.manager.response.RoleResponseDto
import net.corda.messaging.api.publisher.RPCSender
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.Try

class PermissionRoleManagerImpl(
    config: SmartConfig,
    private val rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>,
    private val permissionCache: PermissionCache
) : PermissionRoleManager {

    private val writerTimeout = config.getEndpointTimeout()

    override fun createRole(createRoleRequestDto: CreateRoleRequestDto): Try<RoleResponseDto> {
        return Try.on {
            val future = rpcSender.sendRequest(
                PermissionManagementRequest(
                    createRoleRequestDto.requestedBy,
                    "cluster",
                    CreateRoleRequest(
                        createRoleRequestDto.roleName,
                        createRoleRequestDto.groupVisibility
                    )
                )
            )

            val futureResponse = future.getOrThrow(writerTimeout)

            val result = futureResponse.response
            if (result !is Role)
                throw PermissionManagerException("Unknown response for Create Role operation: $result")

            result.convertToResponseDto()
        }
    }

    override fun getRole(roleRequestDto: GetRoleRequestDto): RoleResponseDto? {
        val cachedRole: Role = permissionCache.getRole(roleRequestDto.roleId) ?: return null
        return cachedRole.convertToResponseDto()
    }
}