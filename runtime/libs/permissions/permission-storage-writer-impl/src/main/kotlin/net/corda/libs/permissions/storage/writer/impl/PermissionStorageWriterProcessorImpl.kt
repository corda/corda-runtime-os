package net.corda.libs.permissions.storage.writer.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.permissions.Permission
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.permission.BulkCreatePermissionsRequest
import net.corda.data.permissions.management.permission.BulkCreatePermissionsResponse
import net.corda.data.permissions.management.permission.CreatePermissionRequest
import net.corda.data.permissions.management.role.AddPermissionToRoleRequest
import net.corda.data.permissions.management.role.CreateRoleRequest
import net.corda.data.permissions.management.role.RemovePermissionFromRoleRequest
import net.corda.data.permissions.management.user.AddRoleToUserRequest
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.data.permissions.management.user.RemoveRoleFromUserRequest
import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.libs.permissions.storage.writer.PermissionStorageWriterProcessor
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
    private val permissionWriter: PermissionWriter
) : PermissionStorageWriterProcessor {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Suppress("ComplexMethod")
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
                is CreateRoleRequest -> {
                    val avroRole = roleWriter.createRole(permissionRequest, request.requestUserId)
                    permissionStorageReader.publishNewRole(avroRole)
                    avroRole
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