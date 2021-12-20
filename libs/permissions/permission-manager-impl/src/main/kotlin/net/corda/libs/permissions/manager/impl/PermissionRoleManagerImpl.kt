package net.corda.libs.permissions.manager.impl

import net.corda.data.permissions.Role
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.role.CreateRoleRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.manager.PermissionRoleManager
import net.corda.libs.permissions.manager.exception.PermissionManagerException
import net.corda.libs.permissions.manager.impl.converter.convertToResponseDto
import net.corda.libs.permissions.manager.request.CreateRoleRequestDto
import net.corda.libs.permissions.manager.request.GetRoleRequestDto
import net.corda.libs.permissions.manager.response.RoleResponseDto
import net.corda.messaging.api.publisher.RPCSender
import net.corda.v5.base.concurrent.getOrThrow
import java.time.Duration

class PermissionRoleManagerImpl(
    config: SmartConfig,
    private val rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>,
    private val permissionCache: PermissionCache
) : PermissionRoleManager {

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

    override fun createRole(createRoleRequestDto: CreateRoleRequestDto): RoleResponseDto {
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

        return result.convertToResponseDto()
    }

    override fun getRole(roleRequestDto: GetRoleRequestDto): RoleResponseDto? {
        val cachedRole: Role = permissionCache.getRole(roleRequestDto.roleId) ?: return null
        return cachedRole.convertToResponseDto()
    }
}