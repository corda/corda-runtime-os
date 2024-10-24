package net.corda.libs.permissions.storage.writer.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.permissions.Permission
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.group.AddRoleToGroupRequest
import net.corda.data.permissions.management.group.ChangeGroupParentIdRequest
import net.corda.data.permissions.management.group.CreateGroupRequest
import net.corda.data.permissions.management.group.DeleteGroupRequest
import net.corda.data.permissions.management.group.RemoveRoleFromGroupRequest
import net.corda.data.permissions.management.permission.BulkCreatePermissionsRequest
import net.corda.data.permissions.management.permission.BulkCreatePermissionsResponse
import net.corda.data.permissions.management.permission.CreatePermissionRequest
import net.corda.data.permissions.management.role.AddPermissionToRoleRequest
import net.corda.data.permissions.management.role.CreateRoleRequest
import net.corda.data.permissions.management.role.RemovePermissionFromRoleRequest
import net.corda.data.permissions.management.user.AddPropertyToUserRequest
import net.corda.data.permissions.management.user.AddRoleToUserRequest
import net.corda.data.permissions.management.user.ChangeUserParentGroupIdRequest
import net.corda.data.permissions.management.user.ChangeUserPasswordRequest
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.data.permissions.management.user.DeleteUserRequest
import net.corda.data.permissions.management.user.RemovePropertyFromUserRequest
import net.corda.data.permissions.management.user.RemoveRoleFromUserRequest
import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.libs.permissions.storage.writer.PermissionStorageWriterProcessor
import net.corda.libs.permissions.storage.writer.impl.group.GroupWriter
import net.corda.libs.permissions.storage.writer.impl.permission.PermissionWriter
import net.corda.libs.permissions.storage.writer.impl.role.RoleWriter
import net.corda.libs.permissions.storage.writer.impl.user.UserWriter
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

class PermissionStorageWriterProcessorImpl(
    private val permissionStorageReaderSupplier: Supplier<PermissionStorageReader?>,
    private val userWriter: UserWriter,
    private val roleWriter: RoleWriter,
    private val groupWriter: GroupWriter,
    private val permissionWriter: PermissionWriter
) : PermissionStorageWriterProcessor {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Suppress("ComplexMethod", "LongMethod")
    override fun onNext(request: PermissionManagementRequest, respFuture: CompletableFuture<PermissionManagementResponse>) {
        try {
            val permissionStorageReader = requireNotNull(permissionStorageReaderSupplier.get())
            val response = when (val permissionRequest = request.request) {
                is CreateUserRequest -> {
                    val avroUser = userWriter.createUser(permissionRequest, request.requestUserId)
                    permissionStorageReader.publishNewUser(avroUser)
                    permissionStorageReader.reconcilePermissionSummaries()
                    avroUser
                }
                is DeleteUserRequest -> {
                    val avroUser = userWriter.deleteUser(permissionRequest, request.requestUserId)
                    permissionStorageReader.publishDeletedUser(avroUser.loginName)
                    permissionStorageReader.reconcilePermissionSummaries()
                    avroUser
                }
                is CreateRoleRequest -> {
                    val avroRole = roleWriter.createRole(permissionRequest, request.requestUserId)
                    permissionStorageReader.publishNewRole(avroRole)
                    avroRole
                }
                is ChangeUserParentGroupIdRequest -> {
                    val avroUser = userWriter.changeUserParentGroup(permissionRequest, request.requestUserId)
                    permissionStorageReader.publishUpdatedUser(avroUser)
                    permissionStorageReader.reconcilePermissionSummaries()
                    avroUser
                }
                is ChangeUserPasswordRequest -> {
                    val avroUser = userWriter.changeUserPassword(permissionRequest, request.requestUserId)
                    permissionStorageReader.publishUpdatedUser(avroUser)
                    permissionStorageReader.reconcilePermissionSummaries()
                    avroUser
                }
                is CreatePermissionRequest -> {
                    val avroPermission = permissionWriter.createPermission(permissionRequest, request.requestUserId, request.virtualNodeId)
                    permissionStorageReader.publishNewPermission(avroPermission)
                    avroPermission
                }
                is BulkCreatePermissionsRequest -> {
                    // Create permissions
                    val permsCreated: List<Permission> =
                        permissionRequest.permissionsToCreate.map { permToCreate: CreatePermissionRequest ->
                            permissionWriter.createPermission(
                                permToCreate,
                                request.requestUserId,
                                request.virtualNodeId
                            )
                        }

                    // Broadcast permissions created
                    permsCreated.map {
                        permissionStorageReader.publishNewPermission(it)
                    }

                    val permIds = permsCreated.map { it.id }

                    // Associate roles with permissions
                    permissionRequest.roleIds.map { roleId ->
                        val avroRoles = permIds.map { permId ->
                            roleWriter.addPermissionToRole(AddPermissionToRoleRequest(roleId, permId), request.requestUserId)
                        }
                        // Once done with a role - broadcast
                        permissionStorageReader.publishUpdatedRole(avroRoles.last())
                    }

                    // Once done with all - post update to permission summaries
                    if (permissionRequest.roleIds.isNotEmpty()) {
                        permissionStorageReader.reconcilePermissionSummaries()
                    }

                    BulkCreatePermissionsResponse(permIds, permissionRequest.roleIds)
                }
                is AddRoleToUserRequest -> {
                    val avroUser = userWriter.addRoleToUser(permissionRequest, request.requestUserId)
                    permissionStorageReader.publishUpdatedUser(avroUser)
                    permissionStorageReader.reconcilePermissionSummaries()
                    avroUser
                }
                is RemoveRoleFromUserRequest -> {
                    val avroUser = userWriter.removeRoleFromUser(permissionRequest, request.requestUserId)
                    permissionStorageReader.publishUpdatedUser(avroUser)
                    permissionStorageReader.reconcilePermissionSummaries()
                    avroUser
                }
                is AddPropertyToUserRequest -> {
                    val avroUser = userWriter.addPropertyToUser(permissionRequest, request.requestUserId)
                    permissionStorageReader.publishUpdatedUser(avroUser)
                    avroUser
                }
                is RemovePropertyFromUserRequest -> {
                    val avroUser = userWriter.removePropertyFromUser(permissionRequest, request.requestUserId)
                    permissionStorageReader.publishUpdatedUser(avroUser)
                    avroUser
                }
                is AddPermissionToRoleRequest -> {
                    val avroRole = roleWriter.addPermissionToRole(permissionRequest, request.requestUserId)
                    permissionStorageReader.publishUpdatedRole(avroRole)
                    permissionStorageReader.reconcilePermissionSummaries()
                    avroRole
                }
                is RemovePermissionFromRoleRequest -> {
                    val avroRole = roleWriter.removePermissionFromRole(permissionRequest, request.requestUserId)
                    permissionStorageReader.publishUpdatedRole(avroRole)
                    permissionStorageReader.reconcilePermissionSummaries()
                    avroRole
                }
                is CreateGroupRequest -> {
                    val avroGroup = groupWriter.createGroup(permissionRequest, request.requestUserId)
                    permissionStorageReader.publishNewGroup(avroGroup)
                    avroGroup
                }
                is ChangeGroupParentIdRequest -> {
                    val avroGroup = groupWriter.changeParentGroup(permissionRequest, request.requestUserId)
                    permissionStorageReader.publishUpdatedGroup(avroGroup)
                    permissionStorageReader.reconcilePermissionSummaries()
                    avroGroup
                }
                is AddRoleToGroupRequest -> {
                    val avroGroup = groupWriter.addRoleToGroup(permissionRequest, request.requestUserId)
                    permissionStorageReader.publishUpdatedGroup(avroGroup)
                    permissionStorageReader.reconcilePermissionSummaries()
                    avroGroup
                }
                is RemoveRoleFromGroupRequest -> {
                    val avroGroup = groupWriter.removeRoleFromGroup(permissionRequest, request.requestUserId)
                    permissionStorageReader.publishUpdatedGroup(avroGroup)
                    permissionStorageReader.reconcilePermissionSummaries()
                    avroGroup
                }
                is DeleteGroupRequest -> {
                    val avroGroup = groupWriter.deleteGroup(permissionRequest, request.requestUserId)
                    permissionStorageReader.publishDeletedGroup(avroGroup.id)
                    avroGroup
                }
                else -> throw IllegalArgumentException("Received invalid permission request type: $permissionRequest")
            }
            respFuture.complete(PermissionManagementResponse(response))
        } catch (e: Exception) {
            log.warn("Failed to execute permission write request.", e)
            respFuture.complete(
                PermissionManagementResponse(
                    ExceptionEnvelope(e::class.java.name, e.message),
                )
            )
        }
    }
}
