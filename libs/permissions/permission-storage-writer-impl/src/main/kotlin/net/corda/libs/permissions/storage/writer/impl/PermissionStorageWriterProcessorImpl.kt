package net.corda.libs.permissions.storage.writer.impl

import java.util.concurrent.CompletableFuture
import net.corda.data.permissions.management.PermissionManagementRequest
import net.corda.data.permissions.management.PermissionManagementResponse
import net.corda.data.permissions.management.permission.CreatePermissionRequest
import net.corda.data.permissions.management.role.AddPermissionToRoleRequest
import net.corda.data.permissions.management.role.CreateRoleRequest
import net.corda.data.permissions.management.role.RemovePermissionFromRoleRequest
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.data.permissions.management.user.AddRoleToUserRequest
import net.corda.data.permissions.management.user.RemoveRoleFromUserRequest
import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.libs.permissions.storage.writer.PermissionStorageWriterProcessor
import net.corda.libs.permissions.storage.writer.impl.permission.PermissionWriter
import net.corda.libs.permissions.storage.writer.impl.role.RoleWriter
import net.corda.libs.permissions.storage.writer.impl.user.UserWriter
import net.corda.v5.base.util.contextLogger

class PermissionStorageWriterProcessorImpl(
    private val permissionStorageReader: PermissionStorageReader,
    private val userWriter: UserWriter,
    private val roleWriter: RoleWriter,
    private val permissionWriter: PermissionWriter
) : PermissionStorageWriterProcessor {

    private companion object {
        val log = contextLogger()
    }

    @Suppress("ComplexMethod")
    override fun onNext(request: PermissionManagementRequest, respFuture: CompletableFuture<PermissionManagementResponse>) {
        try {
            val response = when (val permissionRequest = request.request) {
                is CreateUserRequest -> {
                    val avroUser = userWriter.createUser(permissionRequest, request.requestUserId)
                    permissionStorageReader.publishNewUser(avroUser)
                    avroUser
                }
                is AddRoleToUserRequest -> {
                    val avroUser = userWriter.addRoleToUser(permissionRequest, request.requestUserId)
                    permissionStorageReader.publishNewUser(avroUser)
                    avroUser
                }
                is RemoveRoleFromUserRequest -> {
                    val avroUser = userWriter.removeRoleFromUser(permissionRequest, request.requestUserId)
                    permissionStorageReader.publishNewUser(avroUser)
                    avroUser
                }
                is CreateRoleRequest -> {
                    val avroRole = roleWriter.createRole(permissionRequest, request.requestUserId)
                    permissionStorageReader.publishNewRole(avroRole)
                    avroRole
                }
                is CreatePermissionRequest -> {
                    val avroPermission = permissionWriter.createPermission(permissionRequest, request.requestUserId,
                        request.virtualNodeId)
                    permissionStorageReader.publishNewPermission(avroPermission)
                    avroPermission
                }
                is AddPermissionToRoleRequest -> {
                    val avroRole = roleWriter.addPermissionToRole(permissionRequest, request.requestUserId)
                    permissionStorageReader.publishUpdatedRole(avroRole)
                    avroRole
                }
                is RemovePermissionFromRoleRequest -> {
                    val avroRole = roleWriter.removePermissionFromRole(permissionRequest, request.requestUserId)
                    permissionStorageReader.publishUpdatedRole(avroRole)
                    avroRole
                }
                else -> throw IllegalArgumentException("Received invalid permission request type")
            }
            respFuture.complete(PermissionManagementResponse(response))
        } catch (e: Exception) {
            log.warn(e.message)
            respFuture.completeExceptionally(e)
        }
    }
}