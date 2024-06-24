package net.corda.libs.permissions.manager.impl

import net.corda.data.permissions.Group
import net.corda.data.permissions.User
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.group.CreateGroupRequest
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.management.cache.PermissionManagementCache
import net.corda.libs.permissions.manager.PermissionGroupManager
import net.corda.libs.permissions.manager.impl.SmartConfigUtil.getEndpointTimeout
import net.corda.libs.permissions.manager.impl.converter.convertToResponseDto
import net.corda.libs.permissions.manager.request.CreateGroupRequestDto
import net.corda.libs.permissions.manager.response.GroupResponseDto
import net.corda.messaging.api.publisher.RPCSender
import java.util.concurrent.atomic.AtomicReference

class PermissionGroupManagerImpl(
    restConfig: SmartConfig,
    private val rpcSender: RPCSender<PermissionManagementRequest, PermissionManagementResponse>,
    private val permissionManagementCacheRef: AtomicReference<PermissionManagementCache?>,
) : PermissionGroupManager {

    private val writerTimeout = restConfig.getEndpointTimeout()

    override fun createGroup(createGroupRequestDto: CreateGroupRequestDto): GroupResponseDto {
        val result = sendPermissionWriteRequest<Group>(
            rpcSender,
            writerTimeout,
            PermissionManagementRequest(
                createGroupRequestDto.requestedBy,
                "cluster",
                CreateGroupRequest(
                    createGroupRequestDto.groupName,
                    createGroupRequestDto.parentGroupId,
                    ""
                )
            )
        )
        return result.convertToResponseDto()
    }
}
