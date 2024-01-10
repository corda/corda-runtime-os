package net.corda.libs.permissions.storage.writer.impl.user

import net.corda.data.permissions.management.user.AddRoleToUserRequest
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.data.permissions.management.user.RemoveRoleFromUserRequest
import net.corda.data.permissions.User as AvroUser

/**
 * Responsible for writing user operations to data storage.
 */
interface UserWriter {
    /**
     * Create and persist a User entity and return its Avro representation.
     *
     * @param request CreateUserRequest containing the information of the User to create.
     * @param requestUserId ID of the user who made the request.
     */
    fun createUser(request: CreateUserRequest, requestUserId: String): AvroUser

    /**
     * Associate a Role to a User and return its Avro representation.
     *
     * @param request AddRoleToUserRequest containing the information of the Role and User to associate.
     * @param requestUserId ID of the user who made the request.
     */
    fun addRoleToUser(request: AddRoleToUserRequest, requestUserId: String): AvroUser

    /**
     * Dissociate a Role from a User and return its Avro representation.
     *
     * @param request RemoveRoleFromUserRequest containing the information of the Role and User to dissociate.
     * @param requestUserId ID of the user who made the request.
     */
    fun removeRoleFromUser(request: RemoveRoleFromUserRequest, requestUserId: String): AvroUser
}