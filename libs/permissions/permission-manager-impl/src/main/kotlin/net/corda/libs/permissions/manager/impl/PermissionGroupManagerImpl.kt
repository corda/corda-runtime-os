package net.corda.libs.permissions.manager.impl

import net.corda.data.permissions.Group
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.group.AddRoleToGroupRequest
import net.corda.data.permissions.management.group.ChangeGroupParentIdRequest
import net.corda.data.permissions.management.group.CreateGroupRequest
import net.corda.data.permissions.management.group.DeleteGroupRequest
import net.corda.data.permissions.management.group.RemoveRoleFromGroupRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.management.cache.PermissionManagementCache
import net.corda.libs.permissions.manager.PermissionGroupManager
import net.corda.libs.permissions.manager.impl.SmartConfigUtil.getEndpointTimeout
import net.corda.libs.permissions.manager.impl.converter.convertToResponseDto
import net.corda.libs.permissions.manager.request.AddRoleToGroupRequestDto
import net.corda.libs.permissions.manager.request.ChangeGroupParentIdDto
import net.corda.libs.permissions.manager.request.CreateGroupRequestDto
import net.corda.libs.permissions.manager.request.DeleteGroupRequestDto
import net.corda.libs.permissions.manager.request.RemoveRoleFromGroupRequestDto
import net.corda.libs.permissions.manager.response.GroupContentResponseDto
import net.corda.libs.permissions.manager.response.GroupResponseDto
import net.corda.libs.permissions.manager.response.PropertyResponseDto
import net.corda.libs.permissions.manager.response.RoleAssociationResponseDto
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
                )
            )
        )
        return result.convertToResponseDto()
    }

    override fun changeParentGroup(changeGroupParentIdDto: ChangeGroupParentIdDto): GroupResponseDto {
        val result = sendPermissionWriteRequest<Group>(
            rpcSender,
            writerTimeout,
            PermissionManagementRequest(
                changeGroupParentIdDto.requestedBy,
                null,
                ChangeGroupParentIdRequest(
                    changeGroupParentIdDto.groupId,
                    changeGroupParentIdDto.newParentGroupId,
                )
            )
        )
        return result.convertToResponseDto()
    }

    override fun addRoleToGroup(addRoleToGroupRequestDto: AddRoleToGroupRequestDto): GroupResponseDto {
        val result = sendPermissionWriteRequest<Group>(
            rpcSender,
            writerTimeout,
            PermissionManagementRequest(
                addRoleToGroupRequestDto.requestedBy,
                null,
                AddRoleToGroupRequest(
                    addRoleToGroupRequestDto.groupId,
                    addRoleToGroupRequestDto.roleId,
                )
            )
        )
        return result.convertToResponseDto()
    }

    override fun removeRoleFromGroup(removeRoleFromGroupRequestDto: RemoveRoleFromGroupRequestDto): GroupResponseDto {
        val result = sendPermissionWriteRequest<Group>(
            rpcSender,
            writerTimeout,
            PermissionManagementRequest(
                removeRoleFromGroupRequestDto.requestedBy,
                null,
                RemoveRoleFromGroupRequest(
                    removeRoleFromGroupRequestDto.groupId,
                    removeRoleFromGroupRequestDto.roleId,
                )
            )
        )
        return result.convertToResponseDto()
    }

    override fun getGroupContent(groupId: String): GroupContentResponseDto? {
        val permissionManagementCache = checkNotNull(permissionManagementCacheRef.get()) {
            "Permission management cache is null."
        }
        val cachedGroup: Group = permissionManagementCache.getGroup(groupId) ?: return null
        val subgroups = permissionManagementCache.groups.values
            .filter { it.parentGroupId == groupId }
            .map { it.id }.toSet()
        val userIds = permissionManagementCache.users.values
            .filter { it.parentGroupId == groupId }
            .map { it.id }.toSet()

        return GroupContentResponseDto(
            cachedGroup.id,
            cachedGroup.lastChangeDetails.updateTimestamp,
            cachedGroup.name,
            cachedGroup.parentGroupId,
            cachedGroup.properties.map { PropertyResponseDto(it.lastChangeDetails.updateTimestamp, it.key, it.value) },
            cachedGroup.roleAssociations.map { RoleAssociationResponseDto(it.roleId, it.changeDetails.updateTimestamp) },
            userIds,
            subgroups
        )
    }

    override fun deleteGroup(deleteGroupRequestDto: DeleteGroupRequestDto): GroupResponseDto {
        val result = sendPermissionWriteRequest<Group>(
            rpcSender,
            writerTimeout,
            PermissionManagementRequest(
                deleteGroupRequestDto.requestedBy,
                null,
                DeleteGroupRequest(deleteGroupRequestDto.groupId)
            )
        )
        return result.convertToResponseDto()
    }
}
